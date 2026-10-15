package com.project.handongjudge.hint.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GateResult {
    private final boolean accuracyPass;
    private final boolean leakPass;

    public boolean allPass() {
        return accuracyPass && leakPass;
    }
}
