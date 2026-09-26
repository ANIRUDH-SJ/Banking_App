package com.netbanking.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

class PagedResponseTest {

    @Test
    void mapsContentAndMetadataFromSpringPage() {
        var source = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        var response = PagedResponse.from(source);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }

    @Test
    void copiesContentToKeepTheResponseImmutable() {
        var content = new ArrayList<>(List.of("a"));
        var response = new PagedResponse<>(content, 0, 20, 1, 1, true, true);

        content.add("b");

        assertThat(response.content()).containsExactly("a");
        assertThatThrownBy(() -> response.content().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
