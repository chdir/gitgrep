package ru.drom.gitgrep.server;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@JsonObject
public final class RepositoryResults {
    @JsonField(name = "total_count")
    public int totalCount;

    @JsonField(name = "incomplete_results")
    public boolean incompleteResults;

    @JsonField(name = "items")
    public Repository[] items;
}
