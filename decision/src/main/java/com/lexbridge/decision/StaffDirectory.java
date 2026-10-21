package com.lexbridge.decision;

import java.util.*;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class StaffDirectory {
  private final String providerUrl;
  private final String baseDn;

  StaffDirectory(
      @Value("${app.directory-url:ldap://directory.lexbridge.internal:389}") String u,
      @Value("${app.directory-base:ou=People,dc=lexbridge,dc=test}") String b) {
    providerUrl = u;
    baseDn = b;
  }

  List<String> resolve(String handle) throws Exception {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, providerUrl);
    DirContext ctx = new InitialDirContext(env);
    try {
      SearchControls controls = new SearchControls();
      controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
      NamingEnumeration<SearchResult> rows =
          ctx.search(baseDn, "(&(objectClass=person)(uid=" + handle + "))", controls);
      List<String> out = new ArrayList<>();
      while (rows.hasMore()) {
        Attribute mail = rows.next().getAttributes().get("mail");
        out.add(mail == null ? handle : mail.get().toString());
      }
      return out;
    } finally {
      ctx.close();
    }
  }
}
