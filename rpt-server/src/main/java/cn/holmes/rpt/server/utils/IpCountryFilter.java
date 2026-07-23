package cn.holmes.rpt.server.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.maxmind.db.MaxMindDbConstructor;
import com.maxmind.db.MaxMindDbParameter;
import com.maxmind.db.Reader;
import io.netty.handler.ipfilter.IpFilterRule;
import io.netty.handler.ipfilter.IpFilterRuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IpCountryFilter implements IpFilterRule {

    private static final Logger logger = LoggerFactory.getLogger(IpCountryFilter.class);

    /**
     * 允许访问的国家 ISO 码白名单（大写）。由 {@link #setWhitelist} 注入（来自 server.yml 的 ipFilterCountry）。
     * <p>空集 = 不限制（放行所有）。不再依赖 {@code Locale.getDefault().getCountry()}——那会让白名单跟运行机器的
     * 系统语言走，服务端 LANG=en_US 时会把非 US 客户端全拒。</p>
     */
    private volatile Set<String> whitelist = Collections.emptySet();

    private final Reader reader;

    /**
     * 高性能IP查询结果缓存
     * - 最大4096条，W-TinyLFU淘汰策略，命中率远优于LRU
     * - 写入后1小时过期，应对IP地理位置变更
     * - 线程安全，无锁读取
     */
    private final Cache<String, Boolean> cache = Caffeine.newBuilder().maximumSize(4096).expireAfterWrite(1, TimeUnit.HOURS).build();

    private static final IpCountryFilter INSTANCE = new IpCountryFilter();

    private IpCountryFilter() {
        Reader r = null;
        // 用本类的 classloader 读取 jar 内资源；ClassLoader.getSystemResourceAsStream 用 system classloader，
        // 在 Spring Boot / fat jar 下读不到 jar 内的 Country.mmdb，会导致 reader==null。
        try (InputStream in = IpCountryFilter.class.getResourceAsStream("/Country.mmdb")) {
            if (in != null) {
                r = new Reader(in);
            } else {
                logger.warn("未找到 Country.mmdb，国家白名单过滤将放行所有连接");
            }
        } catch (IOException e) {
            logger.error("加载Country.mmdb失败: {}", e.getMessage());
        }
        this.reader = r;
    }

    public static IpCountryFilter getInstance() {
        return INSTANCE;
    }

    /**
     * 注入允许访问的国家 ISO 码白名单（逗号分隔，如 "CN" 或 "CN,HK"）。null/空 = 不限制（放行所有）。
     * 由 {@code ServerApplication.config()} 在读取 server.yml 后调用。
     */
    public void setWhitelist(String csv) {
        Set<String> set = new LinkedHashSet<>();
        if (csv != null && !csv.trim().isEmpty()) {
            for (String c : csv.split(",")) {
                String code = c.trim().toUpperCase(Locale.ROOT);
                if (!code.isEmpty()) {
                    set.add(code);
                }
            }
        }
        this.whitelist = Collections.unmodifiableSet(set);
        cache.invalidateAll();
        logger.info("国家白名单已更新: {}", set.isEmpty() ? "(空，不限制)" : set);
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        // reader 缺失或白名单为空 → 不命中 REJECT 规则 → 放行（fail-open，避免把所有连接误拒）
        if (reader == null || whitelist.isEmpty()) {
            return false;
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        return Boolean.TRUE.equals(cache.get(ip, k -> lookup(remoteAddress)));
    }

    /**
     * Reader不是线程安全的，需要同步访问。返回 true 表示"命中 REJECT 规则"（即该 IP 应被拒绝）。
     */
    private synchronized boolean lookup(InetSocketAddress remoteAddress) {
        try {
            CountryResponse resp = reader.get(remoteAddress.getAddress(), CountryResponse.class);
            String iso = (resp == null || resp.getCountry() == null) ? null : resp.getCountry().getIsoCode();
            // 查不到国家（如内网/SNAT IP）→ 放行，避免误伤
            if (iso == null) {
                return false;
            }
            boolean reject = !whitelist.contains(iso);
            if (reject) {
                logger.info("国家白名单拒绝连接: ip={}, country={}, 白名单={}",
                        remoteAddress.getAddress().getHostAddress(), iso, whitelist);
            }
            return reject;
        } catch (IOException e) {
            logger.error("IP地理位置查询失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public IpFilterRuleType ruleType() {
        return IpFilterRuleType.REJECT;
    }

    public static class CountryResponse {

        private final Country country;

        public Country getCountry() {
            return country;
        }

        @MaxMindDbConstructor
        public CountryResponse(@MaxMindDbParameter(name = "country") Country country) {
            this.country = country;
        }
    }

    public static class Country {

        private final Integer confidence;
        private final boolean isInEuropeanUnion;
        private final String isoCode;

        @MaxMindDbConstructor
        public Country(@MaxMindDbParameter(name = "confidence") Integer confidence, @MaxMindDbParameter(name = "is_in_european_union") Boolean isInEuropeanUnion, @MaxMindDbParameter(name = "iso_code") String isoCode) {
            this.confidence = confidence;
            this.isInEuropeanUnion = isInEuropeanUnion != null && isInEuropeanUnion;
            this.isoCode = isoCode;
        }

        public Integer getConfidence() {
            return confidence;
        }

        public boolean isInEuropeanUnion() {
            return isInEuropeanUnion;
        }

        public String getIsoCode() {
            return isoCode;
        }
    }
}
