package com.sesame.oss.stripemock.http;

sealed interface TypedResponse<O> permits TypedResponse.Single, TypedResponse.List {
    record Single<O>(int responseCode,
                     O responseEntity) implements TypedResponse<O> {}

    record List<O>(int responseCode,
                   java.util.List<O> responseList) implements TypedResponse<O> {}
}
