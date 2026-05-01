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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class IpCountryFilter implements IpFilterRule {

    private static final Logger logger = LoggerFactory.getLogger(IpCountryFilter.class);

    private static final List<String> WHITE_COUNTRY = Collections.singletonList(Locale.getDefault().getCountry());

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
        try (InputStream in = ClassLoader.getSystemResourceAsStream("Country.mmdb")) {
            if (in != null) {
                r = new Reader(in);
            }
        } catch (IOException e) {
            logger.error("加载Country.mmdb失败: {}", e.getMessage());
        }
        this.reader = r;
    }

    public static IpCountryFilter getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        if (reader == null) {
            return true;
        }
        String ip = remoteAddress.getAddress().getHostAddress();
        return Boolean.TRUE.equals(cache.get(ip, k -> lookup(remoteAddress)));
    }

    /**
     * Reader不是线程安全的，需要同步访问
     */
    private synchronized boolean lookup(InetSocketAddress remoteAddress) {
        try {
            CountryResponse resp = reader.get(remoteAddress.getAddress(), CountryResponse.class);
            if (resp == null || resp.getCountry() == null) {
                return false;
            }
            return !WHITE_COUNTRY.contains(resp.getCountry().getIsoCode());
        } catch (IOException e) {
            logger.error("IP地理位置查询失败: {}", e.getMessage());
            return true;
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
