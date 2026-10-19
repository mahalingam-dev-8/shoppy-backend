package com.shoppy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DurationParserTest {

    @ParameterizedTest(name = "{0} = {1} ms")
    @CsvSource({
            "1d,    86400000",
            "12h,   43200000",
            "30m,   1800000",
            "45s,   45000",
            "500ms, 500",
            "1000,  1000",   // bare number = milliseconds, like the ms library NestJS used
            "PT1H,  3600000" // ISO-8601
    })
    void parsesSupportedFormats(String input, long expectedMillis) {
        assertThat(DurationParser.parse(input)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "10y", "-5m", ""})
    void rejectsUnsupportedFormats(String input) {
        assertThatThrownBy(() -> DurationParser.parse(input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
