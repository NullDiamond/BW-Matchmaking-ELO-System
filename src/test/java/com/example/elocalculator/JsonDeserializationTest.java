package com.example.elocalculator;

import com.example.elocalculator.model.game.PlayerStats;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parameterized and edge case tests for JSON deserialization robustness.
 *
 * These tests ensure that the system can handle various JSON formats and edge cases
 * that might occur in real-world data, preventing future regressions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("JSON Deserialization Robustness Tests")
public class JsonDeserializationTest {

    @TempDir
    static Path sharedTempDir;

    private Gson gson;
    private Path testDataDir;

    @BeforeAll
    static void setupSharedResources() {
        // Setup shared resources if needed
    }

    @BeforeEach
    void setUp() throws IOException {
        testDataDir = sharedTempDir.resolve("data-" + System.nanoTime());
        Files.createDirectories(testDataDir);
        gson = new Gson();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"kills\": 5, \"deaths\": 2, \"bed_breaks\": 3, \"final_kills\": 1}",
        "{\"kills\":5,\"deaths\":2,\"bed_breaks\":3,\"final_kills\":1}",
        "{\n  \"kills\": 5,\n  \"deaths\": 2,\n  \"bed_breaks\": 3,\n  \"final_kills\": 1\n}",
        "{\"kills\": 5, \"deaths\": 2, \"bed_breaks\": 3, \"final_kills\": 1, \"extra_field\": \"ignored\"}",
        "{\"final_kills\": 1, \"bed_breaks\": 3, \"deaths\": 2, \"kills\": 5}"
    })
    @DisplayName("PlayerStats should deserialize correctly from various JSON formats")
    void testPlayerStatsVariousFormats(String json) {
        PlayerStats stats = gson.fromJson(json, PlayerStats.class);

        assertNotNull(stats);
        assertEquals(5, stats.getKills());
        assertEquals(2, stats.getDeaths());
        assertEquals(3, stats.getBedBreaks());
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("Missing fields should default to zero")
    void testMissingFieldsDefaultToZero() {
        String[] testCases = {
            "{\"kills\": 5, \"deaths\": 2}",
            "{\"kills\": 5, \"bed_breaks\": 3}",
            "{\"deaths\": 2, \"final_kills\": 1}",
            "{\"bed_breaks\": 3, \"final_kills\": 1}",
            "{}"
        };

        for (String json : testCases) {
            PlayerStats stats = gson.fromJson(json, PlayerStats.class);
            assertNotNull(stats);

            // Check that missing fields are 0
            if (!json.contains("\"kills\"")) assertEquals(0, stats.getKills());
            if (!json.contains("\"deaths\"")) assertEquals(0, stats.getDeaths());
            if (!json.contains("\"bed_breaks\"")) assertEquals(0, stats.getBedBreaks());
            if (!json.contains("\"final_kills\"")) assertEquals(0, stats.getFinalKills());
        }
    }

    @Test
    @DisplayName("Null values should be handled gracefully")
    void testNullValuesHandling() {
        String jsonWithNulls = """
            {
              "kills": null,
              "deaths": 2,
              "bed_breaks": null,
              "final_kills": 1
            }
            """;

        // Gson will convert null to 0 for primitives
        PlayerStats stats = gson.fromJson(jsonWithNulls, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(0, stats.getKills(), "null kills should become 0");
        assertEquals(2, stats.getDeaths());
        assertEquals(0, stats.getBedBreaks(), "null bed_breaks should become 0");
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("Malformed JSON should throw appropriate exceptions")
    void testMalformedJson() {
        String[] malformedJsons = {
            "{\"kills\": 5, \"deaths\": 2, \"bed_breaks\": 3, \"final_kills\":}", // trailing comma
            "{\"kills\": 5, \"deaths\": 2, \"bed_breaks\": 3, \"final_kills\": 1", // missing closing brace
            "{\"kills\": 5, \"deaths\": 2, \"bed_breaks\": 3, \"final_kills\": 1}}", // extra closing brace
            "{\"kills\": \"five\", \"deaths\": 2}", // wrong type
        };

        for (String json : malformedJsons) {
            assertThrows(JsonSyntaxException.class, () -> {
                gson.fromJson(json, PlayerStats.class);
            }, "Malformed JSON should throw JsonSyntaxException: " + json);
        }
    }

    @Test
    @DisplayName("Extreme values should be handled")
    void testExtremeValues() {
        String extremeJson = """
            {
              "kills": 2147483647,
              "deaths": 0,
              "bed_breaks": 1000000,
              "final_kills": 500000
            }
            """;

        PlayerStats stats = gson.fromJson(extremeJson, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(Integer.MAX_VALUE, stats.getKills());
        assertEquals(0, stats.getDeaths());
        assertEquals(1000000, stats.getBedBreaks());
        assertEquals(500000, stats.getFinalKills());
    }

    @Test
    @DisplayName("Floating point values should cause deserialization to fail")
    void testFloatingPointValues() {
        String floatJson = """
            {
              "kills": 5.7,
              "deaths": 2.3,
              "bed_breaks": 3.9,
              "final_kills": 1.1
            }
            """;

        // Gson throws exception for float values when expecting int
        assertThrows(JsonSyntaxException.class, () -> {
            gson.fromJson(floatJson, PlayerStats.class);
        });
    }

    @Test
    @DisplayName("Scientific notation should work for integer values")
    void testScientificNotation() {
        String scientificJson = """
            {
              "kills": 1e2,
              "deaths": 25,
              "bed_breaks": 3,
              "final_kills": 1
            }
            """;

        PlayerStats stats = gson.fromJson(scientificJson, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(100, stats.getKills());
        assertEquals(25, stats.getDeaths());
        assertEquals(3, stats.getBedBreaks());
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("Unicode and special characters in JSON should be handled")
    void testUnicodeHandling() {
        String unicodeJson = """
            {
              "kills": 5,
              "deaths": 2,
              "bed_breaks": 3,
              "final_kills": 1,
              "note": "Test with üñíçødé characters 🚀"
            }
            """;

        PlayerStats stats = gson.fromJson(unicodeJson, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(5, stats.getKills());
        assertEquals(2, stats.getDeaths());
        assertEquals(3, stats.getBedBreaks());
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("Empty JSON object should create default PlayerStats")
    void testEmptyJsonObject() {
        String emptyJson = "{}";

        PlayerStats stats = gson.fromJson(emptyJson, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(0, stats.getKills());
        assertEquals(0, stats.getDeaths());
        assertEquals(0, stats.getBedBreaks());
        assertEquals(0, stats.getFinalKills());
    }

    @Test
    @DisplayName("Nested JSON structures should be ignored for PlayerStats")
    void testNestedJsonIgnored() {
        String nestedJson = """
            {
              "kills": 5,
              "deaths": 2,
              "bed_breaks": 3,
              "final_kills": 1,
              "nested": {
                "extra": "data",
                "more": [1, 2, 3]
              }
            }
            """;

        PlayerStats stats = gson.fromJson(nestedJson, PlayerStats.class);
        assertNotNull(stats);
        assertEquals(5, stats.getKills());
        assertEquals(2, stats.getDeaths());
        assertEquals(3, stats.getBedBreaks());
        assertEquals(1, stats.getFinalKills());
    }

    @Test
    @DisplayName("Boolean values should cause deserialization to fail")
    void testBooleanValues() {
        String booleanJson = """
            {
              "kills": true,
              "deaths": false,
              "bed_breaks": 3,
              "final_kills": 1
            }
            """;

        // Gson throws exception for boolean values when expecting int
        assertThrows(JsonSyntaxException.class, () -> {
            gson.fromJson(booleanJson, PlayerStats.class);
        });
    }

    @Test
    @DisplayName("Array values should cause deserialization to fail gracefully")
    void testArrayValues() {
        String arrayJson = """
            {
              "kills": [1, 2, 3],
              "deaths": 2,
              "bed_breaks": 3,
              "final_kills": 1
            }
            """;

        assertThrows(JsonSyntaxException.class, () -> {
            gson.fromJson(arrayJson, PlayerStats.class);
        }, "Array values should cause JsonSyntaxException");
    }

    @Test
    @DisplayName("Very large JSON files should be handled")
    void testLargeJsonFile() throws IOException {
        // Create a large JSON with many player stats
        StringBuilder largeJson = new StringBuilder("{\"players\":{");
        for (int i = 0; i < 1000; i++) {
            largeJson.append("\"player").append(i).append("\":")
                    .append("{\"kills\":").append(i % 50)
                    .append(",\"deaths\":").append(i % 30)
                    .append(",\"bed_breaks\":").append(i % 10)
                    .append(",\"final_kills\":").append(i % 5).append("}");
            if (i < 999) largeJson.append(",");
        }
        largeJson.append("}}");

        // This should not crash the system
        assertDoesNotThrow(() -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = gson.fromJson(largeJson.toString(), Map.class);
            assertNotNull(result);
            assertTrue(result.containsKey("players"));
        });
    }

    @Test
    @DisplayName("Concurrent deserialization should work")
    void testConcurrentDeserialization() throws InterruptedException {
        String json = """
            {
              "kills": 5,
              "deaths": 2,
              "bed_breaks": 3,
              "final_kills": 1
            }
            """;

        // Test concurrent access to Gson (it should be thread-safe)
        Runnable deserializationTask = () -> {
            for (int i = 0; i < 100; i++) {
                PlayerStats stats = gson.fromJson(json, PlayerStats.class);
                assertNotNull(stats);
                assertEquals(5, stats.getKills());
                assertEquals(3, stats.getBedBreaks());
            }
        };

        Thread thread1 = new Thread(deserializationTask);
        Thread thread2 = new Thread(deserializationTask);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();
    }
}