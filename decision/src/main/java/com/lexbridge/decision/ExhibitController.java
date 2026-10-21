package com.lexbridge.decision;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
class ExhibitController {
  private final CaseService cases;
  private final byte[] key;
  private final byte[] iv;

  ExhibitController(
      CaseService c, @Value("${app.exhibit-key}") String k, @Value("${app.legacy-iv}") String v) {
    cases = c;
    key = Arrays.copyOf(k.getBytes(StandardCharsets.UTF_8), 16);
    iv = Arrays.copyOf(v.getBytes(StandardCharsets.UTF_8), 16);
  }

  @GetMapping("/exhibits/{seal}/open")
  ResponseEntity<?> open(HttpServletRequest r, @PathVariable String seal) throws Exception {
    byte[] ct;
    try {
      ct = Base64.getUrlDecoder().decode(seal);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(422).body(AuthService.map("error", "exhibit ref malformed"));
    }
    byte[] pt;
    try {
      Cipher cph = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cph.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
      pt = cph.doFinal(ct);
    } catch (BadPaddingException e) {
      return ResponseEntity.status(400).body(AuthService.map("error", "exhibit seal rejected"));
    } catch (IllegalBlockSizeException e) {
      return ResponseEntity.status(422).body(AuthService.map("error", "exhibit length invalid"));
    }
    UUID id;
    try {
      id = UUID.fromString(new String(pt, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(422).body(AuthService.map("error", "exhibit format invalid"));
    }
    Actor a = (Actor) r.getAttribute("actor");
    return ResponseEntity.ok(cases.docView(cases.requireDocument(a, id)));
  }

  @PostMapping("/exhibits/sealed-note")
  ResponseEntity<?> sealedNote(HttpServletRequest r, @RequestBody Map<String, String> body)
      throws Exception {
    byte[] ct;
    try {
      ct = Base64.getUrlDecoder().decode(body.getOrDefault("ref", ""));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(422).body(AuthService.map("error", "sealed note is malformed"));
    }
    Cipher cph = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cph.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
    byte[] pt = cph.doFinal(ct);
    UUID id;
    try {
      id = UUID.fromString(new String(pt, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(422).body(AuthService.map("error", "sealed note format invalid"));
    }
    Actor a = (Actor) r.getAttribute("actor");
    return ResponseEntity.ok(cases.docView(cases.requireDocument(a, id)));
  }
}
