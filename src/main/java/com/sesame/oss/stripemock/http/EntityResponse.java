package com.sesame.oss.stripemock.http;

import java.util.List;

sealed interface EntityResponse permits EntityResponse.Single, EntityResponse.Multiple {
    record Single(int responseCode,
                  Object responseEntity) implements EntityResponse {}

    record Multiple(int responseCode,
                    List<?> responseList) implements EntityResponse {}
}
