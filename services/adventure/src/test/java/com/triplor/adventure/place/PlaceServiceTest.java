package com.triplor.adventure.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlaceServiceTest {

    @Test
    void foldTrimsLowersAndRemovesAccents() {
        assertThat(PlaceService.fold("  Zürich ")).isEqualTo("zurich");
        assertThat(PlaceService.fold("São Paulo")).isEqualTo("sao paulo");
        assertThat(PlaceService.fold("JAIPUR")).isEqualTo("jaipur");
    }

    @Test
    void thePatternEscapesLikeWildcards() {
        assertThat(PlaceService.toPrefixPattern("jai")).isEqualTo("jai%");
        assertThat(PlaceService.toPrefixPattern("50%_off\\")).isEqualTo("50\\%\\_off\\\\%");
    }
}
