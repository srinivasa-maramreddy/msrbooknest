(() => {
  function nationalMobileNumber(value) {
    const digits = value.replace(/\D/g, "");
    const nationalNumber = digits.length === 12 && digits.startsWith("91")
      ? digits.slice(2)
      : digits;

    return /^[6-9]\d{9}$/.test(nationalNumber) ? nationalNumber : null;
  }

  function normalizeLoginMobile(input) {
    if (!input || input.value.includes("@")) return;
    const mobile = nationalMobileNumber(input.value);
    if (mobile) input.value = mobile;
  }

  const registerForm = document.getElementById("kc-register-form");
  const registerMobile = registerForm?.querySelector('[name="username"]');
  if (registerMobile) {
    registerMobile.type = "tel";
    registerMobile.inputMode = "numeric";
    registerMobile.autocomplete = "tel-national";
    registerMobile.placeholder = "10-digit Indian mobile number";
    registerMobile.addEventListener("input", () => {
      registerMobile.setCustomValidity(registerMobile.value && !nationalMobileNumber(registerMobile.value)
        ? "Enter a valid 10-digit Indian mobile number."
        : "");
    });
    registerForm.addEventListener("submit", (event) => {
      const mobile = nationalMobileNumber(registerMobile.value);
      if (!mobile) {
        event.preventDefault();
        registerMobile.setCustomValidity("Enter a valid 10-digit Indian mobile number.");
        registerMobile.reportValidity();
        return;
      }
      registerMobile.value = mobile;
      registerMobile.setCustomValidity("");
    });
  }

  const loginForm = document.getElementById("kc-form-login");
  const loginIdentifier = loginForm?.querySelector('[name="username"]');
  if (loginIdentifier) {
    loginIdentifier.type = "text";
    loginIdentifier.autocomplete = "username";
    loginIdentifier.placeholder = "Email or mobile number";
    loginForm.addEventListener("submit", () => normalizeLoginMobile(loginIdentifier));
  }

  const resetForm = document.getElementById("kc-reset-password-form");
  const resetIdentifier = resetForm?.querySelector('[name="username"]');
  if (resetIdentifier) {
    resetIdentifier.type = "email";
    resetIdentifier.autocomplete = "email";
    resetIdentifier.placeholder = "Email address";
    resetForm.querySelector("label[for='username']")?.replaceChildren("Email address");
  }

  const passwordForm = document.getElementById("kc-passwd-update-form")
    || document.getElementById("kc-register-form");
  const newPassword = passwordForm?.querySelector('[name="password-new"], [name="password"]');
  if (passwordForm && newPassword) {
    newPassword.minLength = 10;

    const help = document.createElement("p");
    help.className = "msr-password-rules";
    help.textContent = "Use at least 10 characters, including at least 1 special character.";
    const field = newPassword.closest(".pf-v5-c-form__group, .form-group") || newPassword.parentElement;
    field?.insertAdjacentElement("afterend", help);

    const updatePasswordValidity = () => {
      const value = newPassword.value;
      if (!value) {
        newPassword.setCustomValidity("");
      } else if (value.length < 10) {
        newPassword.setCustomValidity("Use at least 10 characters.");
      } else if (!/[^A-Za-z0-9]/.test(value)) {
        newPassword.setCustomValidity("Include at least 1 special character.");
      } else {
        newPassword.setCustomValidity("");
      }
    };

    newPassword.addEventListener("input", updatePasswordValidity);
    passwordForm.addEventListener("submit", updatePasswordValidity);
  }
})();
