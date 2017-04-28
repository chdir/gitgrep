package ru.drom.gitgrep.server;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@JsonObject
public final class Repository {
    @JsonField(name = "full_name")
    public String fullName;

    @JsonField
    public Owner owner;

    @JsonField(name = "html_url")
    public String htmlUrl;

    @JsonField
    public String description;
}
