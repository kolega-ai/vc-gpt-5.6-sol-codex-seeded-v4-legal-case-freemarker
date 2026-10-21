package com.lexbridge.channel;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

record LoginForm(@Email @NotBlank String email, @NotBlank String password) {}

record ProfileEdit(@NotBlank @Size(max = 120) String name, String designation) {}

record ViewRole(@NotBlank String viewRole) {}

record MatterForm(
    @NotBlank @Size(max = 200) String title,
    @NotBlank String clientId,
    @NotBlank @Size(max = 100) String practiceArea,
    String attorneyId,
    String paralegalId,
    @Size(max = 30) String sensitivity,
    @Size(max = 3000) String description,
    String targetCloseDate) {}

@Controller
class WebController {
  private final DecisionClient api;

  WebController(DecisionClient a) {
    api = a;
  }

  @GetMapping("/login")
  String login(
      @RequestParam(defaultValue = "") String next, HttpServletResponse response, Model model) {
    if (!next.isBlank()) {
      Cookie c = new Cookie("lb_return", URLEncoder.encode(next, StandardCharsets.UTF_8));
      c.setPath("/");
      c.setHttpOnly(true);
      response.addCookie(c);
    }
    model.addAttribute("next", next);
    return "login";
  }

  @PostMapping("/login")
  String login(
      @Valid LoginForm f,
      BindingResult errors,
      @RequestParam(defaultValue = "false") boolean trustedDevice,
      @CookieValue(name = "lb_return", required = false) String lbReturn,
      HttpServletRequest request,
      Model model) {
    if (errors.hasErrors()) {
      model.addAttribute("error", "Enter a valid email and password.");
      return "login";
    }
    try {
      Map<String, Object> result =
          api.publicPost(
              "/internal/auth/login?trustedDevice=" + trustedDevice,
              Map.of("email", f.email(), "password", f.password()));
      request.changeSessionId();
      request.getSession().setAttribute("token", result.get("token"));
      if (trustedDevice) request.getSession().setMaxInactiveInterval(60 * 60 * 24 * 30);
      String onward = continueLocation(lbReturn);
      return "redirect:" + onward;
    } catch (Exception e) {
      model.addAttribute("error", "Email or password is incorrect, or the account is disabled.");
      return "login";
    }
  }

  private static String continueLocation(String stored) {
    if (stored == null || stored.isBlank()) return "/dashboard";
    return URLDecoder.decode(stored, StandardCharsets.UTF_8);
  }

  @PostMapping("/logout")
  String logout(HttpSession s) {
    try {
      api.postVoid("/internal/auth/logout", Map.of(), s);
    } catch (Exception ignored) {
    }
    s.invalidate();
    return "redirect:/login?loggedOut";
  }

  @GetMapping("/forgot-password")
  String forgot() {
    return "forgot";
  }

  @PostMapping("/forgot-password")
  String forgot(@RequestParam String email, RedirectAttributes ra) {
    try {
      api.publicPost("/internal/auth/forgot", Map.of("email", email));
    } catch (Exception ignored) {
    }
    ra.addFlashAttribute(
        "success", "If that account exists, a reset message was written to the local outbox.");
    return "redirect:/forgot-password";
  }

  @PostMapping("/invite-status")
  String inviteStatus(@RequestParam String email, RedirectAttributes ra) {
    try {
      Map<String, Object> x =
          api.publicPost("/internal/auth/invite-status", Map.of("email", email));
      ra.addFlashAttribute(
          "success", "Invitation for " + x.get("name") + " is " + x.get("status") + ".");
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:/forgot-password";
  }

  @GetMapping("/reset-password")
  String reset(@RequestParam(defaultValue = "") String token, Model m) {
    m.addAttribute("token", token);
    return "reset";
  }

  @PostMapping("/reset-password")
  String reset(
      @RequestParam String token,
      @RequestParam String password,
      HttpServletRequest request,
      RedirectAttributes ra) {
    try {
      Map<String, Object> account =
          api.publicPost("/internal/auth/reset", Map.of("token", token, "password", password));
      Map<String, Object> result =
          api.publicPost(
              "/internal/auth/login",
              Map.of("email", account.get("email"), "password", password));
      request.getSession().setAttribute("token", result.get("token"));
      ra.addFlashAttribute("success", "Password changed. You are signed in.");
      return "redirect:/dashboard";
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
      return "redirect:/reset-password";
    }
  }

  @GetMapping("/")
  String root() {
    return "redirect:/dashboard";
  }

  @GetMapping("/dashboard")
  String dashboard(HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/dashboard", s));
    return "dashboard";
  }

  @GetMapping("/profile")
  String profile() {
    return "profile";
  }

  @PostMapping("/profile")
  String profile(@Valid ProfileEdit f, HttpSession s, RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/auth/profile",
                map("nm", f.name(), "designation", f.designation()),
                s),
        "Profile updated.",
        "/profile",
        ra);
  }

  @GetMapping("/matters")
  String matters(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String assignee,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to,
      @RequestParam(defaultValue = "") String scope,
      HttpSession s,
      Model m) {
    m.addAttribute(
        "matters",
        api.list(
            "/internal/matters?scope="
                + enc(scope)
                + "&q="
                + enc(q)
                + "&status="
                + enc(status)
                + "&assignee="
                + enc(assignee)
                + "&from="
                + enc(from)
                + "&to="
                + enc(to),
            s));
    m.addAttribute("q", q);
    m.addAttribute("status", status);
    m.addAttribute("assignee", assignee);
    m.addAttribute("from", from);
    m.addAttribute("to", to);
    m.addAttribute("scope", scope);
    try {
      m.addAllAttributes(api.get("/internal/directory", s));
    } catch (Exception ignored) {
    }
    return "matters";
  }

  @GetMapping("/matters/new")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String newMatter(HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/directory", s));
    return "matter-form";
  }

  @PostMapping("/matters")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String create(
      @Valid MatterForm f, BindingResult errors, HttpSession s, Model m, RedirectAttributes ra) {
    if (errors.hasErrors()) {
      m.addAllAttributes(api.get("/internal/directory", s));
      m.addAttribute("error", "Review the required fields.");
      return "matter-form";
    }
    try {
      Map<String, Object> x = api.post("/internal/matters", toMap(f), s);
      ra.addFlashAttribute("success", "Matter opened in intake.");
      return "redirect:/matters/" + x.get("id");
    } catch (Exception e) {
      m.addAllAttributes(api.get("/internal/directory", s));
      m.addAttribute("error", DecisionClient.message(e));
      return "matter-form";
    }
  }

  @GetMapping("/matters/{id}")
  String matter(@PathVariable UUID id, HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/matters/" + id, s));
    try {
      m.addAllAttributes(api.get("/internal/directory", s));
    } catch (Exception ignored) {
    }
    return "matter-detail";
  }

  @GetMapping("/matters/{id}/edit")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String editMatter(@PathVariable UUID id, HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/matters/" + id, s));
    return "matter-edit";
  }

  @PostMapping("/matters/{id}")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String editMatter(
      @PathVariable UUID id,
      @RequestParam String title,
      @RequestParam(defaultValue = "") String description,
      @RequestParam(defaultValue = "") String targetCloseDate,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/matters/" + id,
                map("title", title, "description", description, "targetCloseDate", targetCloseDate),
                s),
        "Matter details updated.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/matters/{id}/delete")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String deleteMatter(@PathVariable UUID id, HttpSession s, RedirectAttributes ra) {
    try {
      api.delete("/internal/matters/" + id, s);
      ra.addFlashAttribute("success", "Empty intake matter deleted.");
      return "redirect:/matters";
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
      return "redirect:/matters/" + id;
    }
  }

  @PostMapping("/matters/{id}/transition")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  String transition(
      @PathVariable UUID id, @RequestParam String status, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/matters/" + id + "/transition", Map.of("value", status), s),
        "Matter moved to " + status.toLowerCase().replace('_', ' ') + ".",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/matters/{id}/tasks")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String task(
      @PathVariable UUID id,
      @RequestParam String title,
      @RequestParam(defaultValue = "") String notes,
      @RequestParam(defaultValue = "") String assigneeId,
      @RequestParam(defaultValue = "NORMAL") String priority,
      @RequestParam(defaultValue = "") String dueDate,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/matters/" + id + "/tasks",
                map(
                    "title",
                    title,
                    "notes",
                    notes,
                    "assigneeId",
                    assigneeId,
                    "priority",
                    priority,
                    "dueDate",
                    dueDate),
                s),
        "Task assigned.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/tasks/{taskId}/transition")
  String taskTransition(
      @PathVariable UUID taskId,
      @RequestParam UUID matterId,
      @RequestParam String status,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/tasks/" + taskId + "/transition", Map.of("value", status), s),
        "Task status updated.",
        "/matters/" + matterId,
        ra);
  }

  @PostMapping("/matters/{id}/tasks/cancel")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String cancelTasks(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "withdrawn") String reason,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/matters/" + id + "/tasks/cancel", Map.of("reason", reason), s),
        "Outstanding tasks cancelled.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/matters/{id}/deadlines")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String deadline(
      @PathVariable UUID id,
      @RequestParam String title,
      @RequestParam String dueAt,
      @RequestParam(defaultValue = "") String responsibleId,
      @RequestParam(defaultValue = "7") String reminderDays,
      HttpSession s,
      RedirectAttributes ra) {
    String iso = dueAt.endsWith("Z") ? dueAt : dueAt + ":00Z";
    return action(
        () ->
            api.post(
                "/internal/matters/" + id + "/deadlines",
                map(
                    "title",
                    title,
                    "dueAt",
                    iso,
                    "responsibleId",
                    responsibleId,
                    "reminderDays",
                    reminderDays),
                s),
        "Deadline added.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/matters/{id}/notes")
  String note(
      @PathVariable UUID id,
      @RequestParam String kind,
      @RequestParam String body,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/matters/" + id + "/notes", Map.of("kind", kind, "body", body), s),
        kind.equals("PRIVILEGED_NOTE") ? "Privileged note recorded." : "Message saved.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/messages/{messageId}/approve")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  String approve(
      @PathVariable UUID messageId,
      @RequestParam UUID matterId,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/messages/" + messageId + "/approve", Map.of(), s),
        "Message approved and shared.",
        "/matters/" + matterId,
        ra);
  }

  @PostMapping("/matters/{id}/documents")
  String upload(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "CORRESPONDENCE") String category,
      @RequestParam(defaultValue = "INTERNAL_ONLY") String visibility,
      @RequestParam(defaultValue = "") String tags,
      @RequestParam MultipartFile file,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.upload(
                "/internal/matters/" + id + "/documents",
                map("name", name, "category", category, "visibility", visibility, "tags", tags),
                file,
                s),
        "Document uploaded.",
        "/matters/" + id,
        ra);
  }

  @GetMapping("/documents/{id}")
  String document(@PathVariable UUID id, HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/documents/" + id, s));
    return "document";
  }

  @PostMapping("/documents/{id}/versions")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String version(
      @PathVariable UUID id,
      @RequestParam UUID matterId,
      @RequestParam MultipartFile file,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.upload("/internal/documents/" + id + "/versions", Map.of(), file, s),
        "New document version uploaded.",
        "/documents/" + id,
        ra);
  }

  @PostMapping("/documents/{id}/visibility")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY')")
  String visibility(
      @PathVariable UUID id,
      @RequestParam String visibility,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/documents/" + id + "/visibility", Map.of("value", visibility), s),
        "Sharing controls updated.",
        "/documents/" + id,
        ra);
  }

  @GetMapping("/documents/{id}/download")
  ResponseEntity<byte[]> download(
      @PathVariable UUID id, @RequestParam(required = false) Integer version, HttpSession s) {
    return passthrough(
        api.bytes(
            "/internal/documents/"
                + id
                + "/download"
                + (version == null ? "" : "?version=" + version),
            s));
  }

  @GetMapping("/clients")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String clients(HttpSession s, Model m) {
    m.addAttribute("clients", api.list("/internal/clients", s));
    return "clients";
  }

  @PostMapping("/clients")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String client(
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam(defaultValue = "PERSON") String type,
      @RequestParam(defaultValue = "") String phone,
      @RequestParam(defaultValue = "") String address,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/clients",
                map("name", name, "email", email, "type", type, "phone", phone, "address", address),
                s),
        "Client created.",
        "/clients",
        ra);
  }

  @GetMapping("/calendar")
  String calendar(HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/work", s));
    return "calendar";
  }

  @GetMapping("/search")
  String search(@RequestParam(defaultValue = "") String q, HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/search?q=" + enc(q), s));
    return "search";
  }

  @GetMapping("/reports")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String reports() {
    return "reports";
  }

  @GetMapping("/reports/{name}.csv")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  ResponseEntity<byte[]> report(
      @PathVariable String name,
      @RequestParam(defaultValue = "") String saveAs,
      HttpSession s) {
    if (!Set.of("deadlines", "matters", "documents").contains(name))
      return ResponseEntity.notFound().build();
    return passthrough(
        api.bytes(
            "/internal/reports/" + name + ".csv" + (saveAs.isBlank() ? "" : "?saveAs=" + enc(saveAs)),
            s));
  }

  @PostMapping("/documents/{id}/convert")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String convert(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "pdf") String profile,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/documents/" + id + "/convert/" + profile, Map.of(), s),
        "Document converted.",
        "/documents/" + id,
        ra);
  }

  @PostMapping("/documents/{id}/access-code")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String accessCode(@PathVariable UUID id, HttpSession s, RedirectAttributes ra) {
    try {
      Map<String, Object> x = api.post("/internal/documents/" + id + "/access-code", Map.of(), s);
      ra.addFlashAttribute("success", "Access code issued: " + x.get("code"));
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:/documents/" + id;
  }

  @PostMapping("/documents/{id}/open")
  String openDocument(
      @PathVariable UUID id,
      @RequestParam String pass,
      HttpServletResponse response,
      HttpSession s,
      RedirectAttributes ra) {
    Cookie c = new Cookie("lb_view", pass);
    c.setPath("/");
    response.addCookie(c);
    return action(
        () -> api.getWithCookie("/internal/documents/" + id + "/open", "lb_view=" + pass, s),
        "Document unlocked.",
        "/documents/" + id,
        ra);
  }

  @GetMapping("/documents/exhibit-text")
  @ResponseBody
  String exhibitText(@RequestParam String ref, HttpSession s) {
    return (String)
        api.getWithHeader("/internal/exhibits/text", "X-Exhibit-Ref", ref, s).get("text");
  }

  @GetMapping("/exhibits/{seal}")
  @ResponseBody
  Map<String, Object> exhibit(@PathVariable String seal, HttpSession s) {
    return api.get("/internal/exhibits/" + seal + "/open", s);
  }

  @PostMapping("/deadlines/preview")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String previewRule(@RequestParam String rule, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.postWithHeader("/internal/deadlines/preview", "X-Rule-Expr", rule, s),
        "Deadline formula evaluated.",
        "/calendar",
        ra);
  }

  @PostMapping("/firm/matters/reassign")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String reassign(
      @RequestParam UUID fromId,
      @RequestParam UUID toId,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/firm/matters/reassign?fromId=" + fromId + "&toId=" + toId, Map.of(), s),
        "Matter assignments transferred.",
        "/admin/users",
        ra);
  }

  @PostMapping("/matters/{id}/case-files")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String caseFile(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "") String text,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/matters/" + id + "/case-files", Map.of("text", text), s),
        "Case file note stored.",
        "/matters/" + id,
        ra);
  }

  @PostMapping("/firm/bundles/restore")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String restoreBundle(
      @RequestParam UUID matterId,
      @RequestParam String bundle,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/matters/" + matterId + "/legacy-bundle?bundle=" + enc(bundle), Map.of(), s),
        "Legacy bundle restored.",
        "/admin/settings",
        ra);
  }

  @PostMapping("/firm/notices/preview")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String noticePreview(@RequestParam String body, HttpSession s, RedirectAttributes ra) {
    try {
      Map<String, Object> x =
          api.post("/internal/firm/notices/render?body=" + enc(body), Map.of(), s);
      ra.addFlashAttribute("success", "Rendered notice: " + x.get("rendered"));
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:/admin/settings";
  }

  @GetMapping("/api/matter-search")
  @ResponseBody
  List<Map<String, Object>> apiMatterSearch(
      @RequestParam(defaultValue = "") String q, HttpSession s) {
    return api.list("/internal/matters?q=" + enc(q), s);
  }

  @PostMapping(value = "/api/workspace-view", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  Map<String, String> workspaceView(@RequestBody @Valid ViewRole v, HttpServletRequest request) {
    request.getSession(true).setAttribute("viewRole", v.viewRole());
    return map("role", v.viewRole());
  }

  @PostMapping("/firm/imports/receipts")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String importReceipts(@RequestParam MultipartFile file, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.upload("/internal/firm/filing-receipts", Map.of(), file, s),
        "Court filing receipts imported.",
        "/admin/imports",
        ra);
  }

  @PostMapping("/firm/settings/profile-bundle")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String profileBundle(@RequestParam MultipartFile file, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.upload("/internal/firm/profile-bundle", Map.of(), file, s),
        "Firm profile bundle applied.",
        "/admin/settings",
        ra);
  }

  @PostMapping("/firm/settings/profile-apply")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String profileApply(@RequestParam String bundle, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/firm/profile-bundle/apply", Map.of("value", bundle), s),
        "Firm profile bundle applied.",
        "/admin/settings",
        ra);
  }

  @PostMapping("/firm/directory-handle")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String directoryHandle(
      @RequestParam String handle, HttpServletResponse response, RedirectAttributes ra) {
    Cookie c = new Cookie("lb_staff", handle);
    c.setPath("/");
    response.addCookie(c);
    ra.addFlashAttribute("success", "Directory handle remembered.");
    return "redirect:/firm/directory";
  }

  @GetMapping("/firm/directory")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  @ResponseBody
  Map<String, Object> directory(
      @CookieValue(name = "lb_staff", required = false) String handle, HttpSession s) {
    return api.getWithCookie("/internal/directory/registry", "lb_staff=" + nz(handle), s);
  }

  @PostMapping("/firm/exhibits/sealed-note")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  @ResponseBody
  Map<String, Object> sealedNote(@RequestParam String ref, HttpSession s) {
    return api.post("/internal/exhibits/sealed-note", Map.of("ref", ref), s);
  }

  @PostMapping("/firm/messages/preview")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String messagePreview(@RequestParam String body, HttpSession s, RedirectAttributes ra) {
    try {
      Map<String, Object> x =
          api.post("/internal/firm/message-templates/preview", Map.of("value", body), s);
      ra.addFlashAttribute("success", "Rendered message: " + x.get("rendered"));
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:/admin/settings";
  }

  @GetMapping("/firm/register/view")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  ResponseEntity<byte[]> registerView(
      @RequestParam(defaultValue = "") String caption, HttpSession s) {
    return passthrough(
        api.bytesWithHeader("/internal/firm/register/view", "X-Register-Caption", caption, s));
  }

  @PostMapping("/matters/{id}/case-file-info")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  @ResponseBody
  Map<String, Object> caseFileInfo(
      @PathVariable UUID id, @RequestParam String storedName, HttpSession s) {
    return api.post(
        "/internal/matters/" + id + "/case-file-info", Map.of("storedName", storedName), s);
  }

  @GetMapping("/registry-signin")
  String registryForm() {
    return "registry";
  }

  @PostMapping("/registry-signin")
  String registrySignIn(
      @RequestParam String account,
      @RequestParam("registryKey") String registryKey,
      HttpServletRequest request,
      RedirectAttributes ra) {
    try {
      Map<String, Object> result =
          api.publicPostWithHeaders(
              "/internal/registry/signin",
              Map.of("X-Registry-Account", account, "X-Registry-Phrase", registryKey));
      request.getSession().setAttribute("token", result.get("token"));
      return "redirect:/dashboard";
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
      return "redirect:/registry-signin";
    }
  }

  @PostMapping("/register")
  String registerFilter(
      @RequestParam String court, HttpServletResponse response, RedirectAttributes ra) {
    Cookie c = new Cookie("lb_court", court);
    c.setPath("/");
    response.addCookie(c);
    ra.addFlashAttribute("success", "Court register filter updated.");
    return "redirect:/register";
  }

  @GetMapping("/register")
  @ResponseBody
  List<Map<String, String>> register(
      @CookieValue(name = "lb_court", required = false) String court) {
    return courtRegister(court == null || court.isBlank() ? "%" : court);
  }

  private static List<Map<String, String>> courtRegister(String court) {
    try {
      InputStream in = WebController.class.getResourceAsStream("/court-register.xml");
      Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
      String ex = "//filing[court='" + court + "']";
      XPathExpression exp = XPathFactory.newInstance().newXPath().compile(ex);
      NodeList nodes = (NodeList) exp.evaluate(doc, XPathConstants.NODESET);
      List<Map<String, String>> out = new ArrayList<>();
      for (int i = 0; i < nodes.getLength(); i++) {
        Element el = (Element) nodes.item(i);
        out.add(
            map(
                "court", el.getElementsByTagName("court").item(0).getTextContent(),
                "title", el.getElementsByTagName("title").item(0).getTextContent()));
      }
      return out;
    } catch (Exception e) {
      throw new IllegalStateException("Register query failed");
    }
  }

  @GetMapping("/admin/users")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String users(HttpSession s, Model m) {
    m.addAttribute("users", api.list("/internal/admin/users", s));
    return "users";
  }

  @PostMapping("/admin/users/{id}/role")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String role(
      @PathVariable UUID id, @RequestParam String role, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/admin/users/" + id + "/role", Map.of("value", role), s),
        "Role updated.",
        "/admin/users",
        ra);
  }

  @PostMapping("/admin/users/{id}/enabled")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String enabled(
      @PathVariable UUID id, @RequestParam boolean enabled, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/admin/users/" + id + "/enabled", Map.of("enabled", enabled), s),
        enabled ? "Account enabled." : "Account disabled.",
        "/admin/users",
        ra);
  }

  @PostMapping("/admin/users")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String invite(
      @RequestParam String name,
      @RequestParam String email,
      @RequestParam String role,
      @RequestParam(defaultValue = "") String clientId,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/admin/users",
                map("name", name, "email", email, "role", role, "clientId", clientId),
                s),
        "Invitation written to the local outbox.",
        "/admin/users",
        ra);
  }

  @GetMapping("/admin/settings")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String settings(@RequestParam(defaultValue = "") String section, HttpSession s, Model m) {
    m.addAttribute("settings", api.get("/internal/admin/settings?section=" + enc(section), s));
    m.addAllAttributes(api.get("/internal/directory", s));
    return "settings";
  }

  @PostMapping("/admin/settings/{key}")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String setting(
      @PathVariable String key, @RequestParam String value, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> {
          api.postVoid("/internal/admin/settings/" + key, Map.of("value", value), s);
          return Map.of();
        },
        "Setting updated.",
        "/admin/settings",
        ra);
  }

  @PostMapping("/admin/settings/brand-asset")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String brandAsset(
      @RequestParam(defaultValue = "") String assetName,
      @RequestParam MultipartFile file,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.upload("/internal/admin/brand-asset?assetName=" + enc(assetName), Map.of(), file, s),
        "Brand asset stored.",
        "/admin/settings",
        ra);
  }

  @GetMapping("/brand-asset")
  ResponseEntity<byte[]> brandAsset(HttpSession s) {
    return passthrough(api.bytes("/internal/admin/brand-asset", s));
  }

  @PostMapping("/admin/practice-areas")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String practice(@RequestParam String name, HttpSession s, RedirectAttributes ra) {
    return action(
        () -> api.post("/internal/admin/practice-areas", Map.of("name", name), s),
        "Practice area created.",
        "/admin/settings",
        ra);
  }

  @PostMapping("/admin/practice-areas/{id}/active")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String practiceActive(
      @PathVariable UUID id, @RequestParam boolean active, HttpSession s, RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/admin/practice-areas/" + id + "/active", Map.of("active", active), s),
        "Practice area updated.",
        "/admin/settings",
        ra);
  }

  @GetMapping("/admin/audit")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String audit(HttpSession s, Model m) {
    m.addAttribute("events", api.list("/internal/admin/audit", s));
    return "audit";
  }

  @GetMapping("/admin/integrations")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String integrations(HttpSession s, Model m) {
    m.addAllAttributes(api.get("/internal/admin/integrations", s));
    return "integrations";
  }

  @PostMapping("/admin/integrations/api-keys")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String apiKey(@RequestParam String name, HttpSession s, RedirectAttributes ra) {
    try {
      Map<String, Object> x =
          api.post("/internal/admin/integrations/api-keys", Map.of("name", name), s);
      ra.addFlashAttribute("success", "API key created. Copy it now: " + x.get("secret"));
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:/admin/integrations";
  }

  @PostMapping("/admin/integrations/webhooks/test")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String webhook(@RequestParam String targetUrl, HttpSession s, RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/admin/integrations/webhooks/test", Map.of("targetUrl", targetUrl), s),
        "Local webhook delivery simulated.",
        "/admin/integrations",
        ra);
  }

  @PostMapping("/admin/integrations/webhooks/deliver")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String deliverWebhook(
      @RequestParam String targetUrl,
      @RequestParam(defaultValue = "matter.updated") String eventType,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/admin/integrations/webhooks/deliver",
                Map.of("targetUrl", targetUrl, "eventType", eventType),
                s),
        "Delivery attempted.",
        "/admin/integrations",
        ra);
  }

  @GetMapping("/admin/imports")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String imports() {
    return "imports";
  }

  @PostMapping("/admin/imports/{type}")
  @PreAuthorize("hasRole('FIRM_ADMIN')")
  String importCsv(
      @PathVariable String type,
      @RequestParam MultipartFile file,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () -> api.upload("/internal/admin/import/" + type, Map.of(), file, s),
        "CSV imported.",
        "/admin/imports",
        ra);
  }

  @PostMapping("/admin/imports/filings")
  @PreAuthorize("hasAnyRole('FIRM_ADMIN','ATTORNEY','PARALEGAL')")
  String importFilings(
      @RequestParam String matterId,
      @RequestParam String manifestXml,
      HttpSession s,
      RedirectAttributes ra) {
    return action(
        () ->
            api.post(
                "/internal/admin/import/filings",
                Map.of("matterId", matterId, "manifestXml", manifestXml),
                s),
        "Filing manifest imported.",
        "/admin/imports",
        ra);
  }

  private String action(Throwing work, String success, String redirect, RedirectAttributes ra) {
    try {
      work.run();
      ra.addFlashAttribute("success", success);
    } catch (Exception e) {
      ra.addFlashAttribute("error", DecisionClient.message(e));
    }
    return "redirect:" + redirect;
  }

  private static Map<String, String> toMap(MatterForm f) {
    return map(
        "title",
        f.title(),
        "clientId",
        f.clientId(),
        "practiceArea",
        f.practiceArea(),
        "attorneyId",
        nz(f.attorneyId()),
        "paralegalId",
        nz(f.paralegalId()),
        "sensitivity",
        nz(f.sensitivity()),
        "description",
        nz(f.description()),
        "targetCloseDate",
        nz(f.targetCloseDate()));
  }

  private static Map<String, String> map(String... v) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i < v.length; i += 2) m.put(v[i], nz(v[i + 1]));
    return m;
  }

  private static String nz(String v) {
    return v == null ? "" : v;
  }

  private static String enc(String v) {
    return URLEncoder.encode(v, StandardCharsets.UTF_8);
  }

  private static ResponseEntity<byte[]> passthrough(ResponseEntity<byte[]> r) {
    HttpHeaders h = new HttpHeaders();
    Optional.ofNullable(r.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE))
        .ifPresent(x -> h.set(HttpHeaders.CONTENT_TYPE, x));
    Optional.ofNullable(r.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .ifPresent(x -> h.set(HttpHeaders.CONTENT_DISPOSITION, x));
    return new ResponseEntity<>(r.getBody(), h, r.getStatusCode());
  }

  interface Throwing {
    Object run() throws Exception;
  }
}

@ControllerAdvice
class ViewAdvice {
  @ModelAttribute
  void globals(HttpServletRequest r, Model m) {
    Object u = r.getAttribute("currentUser");
    if (u != null) m.addAttribute("currentUser", u);
    m.addAttribute("assetVersion", AssetVersion.value());
  }

  @ExceptionHandler(HttpStatusCodeException.class)
  String apiError(HttpStatusCodeException e, HttpServletResponse response, Model model) {
    response.setStatus(e.getStatusCode().value());
    model.addAttribute("status", e.getStatusCode().value());
    return "error";
  }
}

final class AssetVersion {
  private AssetVersion() {}

  static String value() {
    /* java.util.Random is non-cryptographic here: it only cache-busts static asset URLs. */
    return Integer.toHexString(new java.util.Random().nextInt());
  }
}
