package ru.drom.gitgrep.server;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@JsonObject
public final class Owner {
    @JsonField(name = "avatar_url")
    public String avatarUrl;

    @JsonField
    public String id;
}
