package com.framework.log.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogDesensitizeUtilsTest {

    @Test
    void desensitizesSensitiveFieldsInsideRootArray() {
        String json = """
                [
                  {
                    "username": "alice",
                    "password": "secret",
                    "phone": "13812345678",
                    "children": [
                      {
                        "token": "access-token",
                        "email": "alice@example.com"
                      }
                    ]
                  }
                ]
                """;

        String desensitized = LogDesensitizeUtils.desensitize(json);

        assertThat(desensitized)
                .contains("\"password\":\"***\"")
                .contains("\"token\":\"***\"")
                .contains("\"phone\":\"138****5678\"")
                .contains("\"email\":\"a***@example.com\"")
                .doesNotContain("secret")
                .doesNotContain("access-token");
    }

    @Test
    void desensitizesSensitiveQueryStringPairs() {
        String queryString = "username=alice&password=secret&token=access-token&phone=13812345678";

        String desensitized = LogDesensitizeUtils.desensitize(queryString);

        assertThat(desensitized)
                .contains("password=***")
                .contains("token=***")
                .contains("phone=138****5678")
                .doesNotContain("secret")
                .doesNotContain("access-token");
    }
}
