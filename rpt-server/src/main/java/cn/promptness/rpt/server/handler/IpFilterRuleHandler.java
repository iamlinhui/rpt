package cn.promptness.rpt.server.handler;

import com.maxmind.db.CHMCache;
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
import java.util.Objects;

public class IpFilterRuleHandler implements IpFilterRule {

    private final Logger logger = LoggerFactory.getLogger(IpFilterRuleHandler.class);

    private Reader reader;

    private static final List<String> WHITE_COUNTRY = Collections.singletonList(Locale.getDefault().getCountry());

    public IpFilterRuleHandler() {
        try (InputStream inputStream = ClassLoader.getSystemResourceAsStream("Country.mmdb")) {
            if (inputStream == null) {
                return;
            }
            reader = new Reader(inputStream, new CHMCache());
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }
    }

    @Override
    public boolean matches(InetSocketAddress remoteAddress) {
        if (Objects.isNull(reader)) {
            return true;
        }
        try {
            CountryResponse countryResponse = reader.get(remoteAddress.getAddress(), CountryResponse.class);
            if (Objects.isNull(countryResponse)) {
                return false;
            }
            return !WHITE_COUNTRY.contains(countryResponse.getCountry().getIsoCode());
        } catch (IOException ioException) {
            logger.error(ioException.getMessage());
        }
        return true;
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
