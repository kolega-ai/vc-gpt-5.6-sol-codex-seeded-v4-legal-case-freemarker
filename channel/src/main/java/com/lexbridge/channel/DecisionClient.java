package com.lexbridge.channel;

import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.*;
import org.springframework.web.client.*;
import org.springframework.web.multipart.MultipartFile;

@Component
class DecisionClient {
  private final RestClient client;

  DecisionClient(RestClient.Builder b, @Value("${app.decision-url}") String url) {
    client = b.baseUrl(url).build();
  }

  Map<String, Object> publicPost(String path, Object body) {
    return client
        .post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  Map<String, Object> get(String path, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  List<Map<String, Object>> list(String path, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  Map<String, Object> getWithHeader(String path, String name, String value, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .header(name, value)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  Map<String, Object> getWithCookie(String path, String cookie, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .header("Cookie", cookie)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  Map<String, Object> publicPostWithHeaders(String path, Map<String, String> headers) {
    return client
        .post()
        .uri(path)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(h -> headers.forEach(h::set))
        .body(Map.of())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  ResponseEntity<byte[]> bytesWithHeader(String path, String name, String value, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .header(name, value)
        .retrieve()
        .toEntity(byte[].class);
  }

  Map<String, Object> postWithHeader(String path, String name, String value, HttpSession s) {
    return client
        .post()
        .uri(path)
        .header("X-Session-Token", token(s))
        .header(name, value)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  Map<String, Object> post(String path, Object body, HttpSession s) {
    return client
        .post()
        .uri(path)
        .header("X-Session-Token", token(s))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  void postVoid(String path, Object body, HttpSession s) {
    client
        .post()
        .uri(path)
        .header("X-Session-Token", token(s))
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  void delete(String path, HttpSession s) {
    client.delete().uri(path).header("X-Session-Token", token(s)).retrieve().toBodilessEntity();
  }

  Map<String, Object> upload(
      String path, Map<String, String> fields, MultipartFile file, HttpSession s) throws Exception {
    LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    fields.forEach(parts::add);
    parts.add(
        "file",
        new ByteArrayResource(file.getBytes()) {
          @Override
          public String getFilename() {
            return file.getOriginalFilename();
          }
        });
    return client
        .post()
        .uri(path)
        .header("X-Session-Token", token(s))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  ResponseEntity<byte[]> bytes(String path, HttpSession s) {
    return client
        .get()
        .uri(path)
        .header("X-Session-Token", token(s))
        .retrieve()
        .toEntity(byte[].class);
  }

  private String token(HttpSession s) {
    Object t = s.getAttribute("token");
    if (t == null) throw new IllegalStateException("Session unavailable");
    return t.toString();
  }

  static String message(Exception e) {
    if (e instanceof HttpStatusCodeException h) {
      try {
        String b = h.getResponseBodyAsString();
        int p = b.indexOf("\"error\":\"");
        if (p >= 0) {
          String x = b.substring(p + 9);
          return x.substring(0, x.indexOf('"'));
        }
      } catch (Exception ignored) {
      }
      if (h.getStatusCode().value() == 403)
        return "You do not have permission to perform that action.";
    }
    return Objects.requireNonNullElse(e.getMessage(), "The request could not be completed.");
  }
}
