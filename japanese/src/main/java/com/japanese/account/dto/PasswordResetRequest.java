package com.japanese.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetRequest {
    @NotBlank private String token;
    @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.") private String password;
    @NotBlank private String passwordConfirmation;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getPasswordConfirmation() { return passwordConfirmation; }
    public void setPasswordConfirmation(String passwordConfirmation) { this.passwordConfirmation = passwordConfirmation; }
    @Override public String toString() { return "PasswordResetRequest[REDACTED]"; }
}
