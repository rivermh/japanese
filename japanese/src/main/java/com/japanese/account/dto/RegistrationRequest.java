package com.japanese.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {

    @NotBlank(message = "로그인 ID를 입력하세요.")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,80}$", message = "로그인 ID는 영문, 숫자, 점, 밑줄, 하이픈으로 3~80자여야 합니다.")
    private String loginId;

    @Email(message = "이메일 형식을 확인하세요.")
    @Size(max = 160, message = "이메일은 160자 이하여야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
    private String password;

    @NotBlank(message = "표시 이름을 입력하세요.")
    @Size(max = 80, message = "표시 이름은 80자 이하여야 합니다.")
    private String displayName;

    public String getLoginId() { return loginId; }
    public void setLoginId(String loginId) { this.loginId = loginId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
