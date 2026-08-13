(() => {
  const themeModes = new Set(["system", "light", "dark"]);
  const systemTheme = window.matchMedia("(prefers-color-scheme: dark)");

  function storedTheme() {
    const value = localStorage.getItem("reiTheme") || "system";
    return themeModes.has(value) ? value : "system";
  }

  function applyTheme(mode = storedTheme(), persist = true) {
    const selected = themeModes.has(mode) ? mode : "system";
    if (persist) localStorage.setItem("reiTheme", selected);
    const dark = selected === "dark" || (selected === "system" && systemTheme.matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    document.querySelectorAll("[data-admin-theme]").forEach(button => {
      const active = button.dataset.adminTheme === selected;
      button.classList.toggle("active", active);
      button.setAttribute("aria-checked", String(active));
    });
  }

  window.applyAdminTheme = mode => applyTheme(mode, true);
  applyTheme(storedTheme(), false);
  systemTheme.addEventListener?.("change", () => {
    if (storedTheme() === "system") applyTheme("system", false);
  });

  window.closeAdminModal = () => document.querySelector(".admin-modal")?.remove();
  window.openAdminSettings = event => {
    event?.preventDefault();
    event?.stopPropagation();
    window.closeAdminModal();
    const modal = document.createElement("div");
    modal.className = "modal admin-modal";
    modal.setAttribute("role", "dialog");
    modal.setAttribute("aria-modal", "true");
    modal.setAttribute("aria-labelledby", "adminSettingsTitle");
    modal.innerHTML = `<section class="card admin-settings-card">
      <div class="row"><div><h2 id="adminSettingsTitle">Configurações da conta</h2><p class="muted">Personalize a aparência e altere sua senha pessoal.</p></div></div>
      <section class="theme-settings">
        <h3>Tema do sistema</h3><p class="muted">Escolha a aparência que deseja utilizar em todas as telas.</p>
        <div class="theme-options" role="radiogroup" aria-label="Tema do sistema">
          <button type="button" class="theme-option" data-admin-theme="system" role="radio" onclick="applyAdminTheme('system')">Sistema</button>
          <button type="button" class="theme-option" data-admin-theme="light" role="radio" onclick="applyAdminTheme('light')">Claro</button>
          <button type="button" class="theme-option" data-admin-theme="dark" role="radio" onclick="applyAdminTheme('dark')">Escuro</button>
        </div>
      </section>
      <form id="adminChangePasswordForm">
        <h3>Alterar minha senha</h3>
        <label>Senha atual</label><input name="currentPassword" type="password" autocomplete="current-password" required>
        <div class="admin-password-grid">
          <div><label>Nova senha</label><input name="newPassword" type="password" minlength="8" autocomplete="new-password" required></div>
          <div><label>Confirmar nova senha</label><input name="confirmation" type="password" minlength="8" autocomplete="new-password" required></div>
        </div>
        <div class="password-actions"><button class="btn" type="submit">Alterar senha</button></div>
      </form>
      <div class="admin-settings-actions">
        <button class="btn secondary" type="button" onclick="closeAdminModal()">Fechar</button>
        <form method="post" action="/admin/logout"><button class="btn danger" type="submit">Sair do sistema</button></form>
      </div>
    </section>`;
    document.body.appendChild(modal);
    applyTheme(storedTheme(), false);
    modal.addEventListener("click", click => {
      if (click.target === modal) window.closeAdminModal();
    });
    modal.querySelector("#adminChangePasswordForm").addEventListener("submit", async submit => {
      submit.preventDefault();
      const payload = Object.fromEntries(new FormData(submit.currentTarget));
      if (payload.newPassword !== payload.confirmation) {
        window.alert("A confirmação da nova senha não confere.");
        return;
      }
      try {
        const response = await fetch("/api/auth/change-password", {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || "Não foi possível alterar a senha.");
        submit.currentTarget.reset();
        window.alert("Senha alterada com sucesso.");
      } catch (error) {
        window.alert(error.message || "Não foi possível alterar a senha.");
      }
    });
    modal.querySelector(".theme-option.active")?.focus();
  };

  document.addEventListener("keydown", event => {
    if (event.key === "Escape") window.closeAdminModal();
  });
})();
