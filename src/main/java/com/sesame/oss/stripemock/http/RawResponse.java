package com.sesame.oss.stripemock.http;

import com.sun.net.httpserver.Headers;

record RawResponse(int code,
                   String body,
                   Headers headers,
                   String requestId) {

}
