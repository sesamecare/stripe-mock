package com.sesame.oss.stripemock.http;

import com.stripe.net.ApiResource;

import java.util.List;

sealed interface EntityResponse permits EntityResponse.Single, EntityResponse.Multiple {
    record Single(int responseCode,
                  ApiResource responseEntity) implements EntityResponse {}

    record Multiple(int responseCode,
                    List<?> responseList) implements EntityResponse {}
}
