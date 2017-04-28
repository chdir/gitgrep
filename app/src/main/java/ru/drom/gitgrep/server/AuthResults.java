package ru.drom.gitgrep.server;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@JsonObject
public final class AuthResults {
    @JsonField(name = "access_token")
    public String accessToken;

    @JsonField
    public String scope;

    @JsonField(name = "token_type")
    public String tokenType;
}
