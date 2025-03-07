/*
 *    Copyright (c) 2025, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.webauthn.data;

import com.webauthn4j.credential.CredentialRecord;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.client.CollectedClientData;
import io.supertokens.pluginInterface.webauthn.WebAuthNStoredCredential;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class is needed for the webauthn4j library. It expects the credential to be a `CredentialRecord` object.
 * Because of this, we are wrapping our own Credential and other required objects in this class.
 */
public class WebauthNCredentialRecord implements CredentialRecord {

    private AAGUID aaguid;
    private byte[] credentialId;
    private COSEKey coseKey;

    private WebAuthNStoredCredential storedCredential;

    public WebauthNCredentialRecord(AAGUID aaguid, byte[] credentialId, COSEKey coseKey, WebAuthNStoredCredential storedCredential) {
        this.aaguid = aaguid;
        this.credentialId = credentialId;
        this.coseKey = coseKey;
        this.storedCredential = storedCredential;
    }
    @Override
    public @Nullable CollectedClientData getClientData() {
        return null;
    }

    @Nullable
    @Override
    public Boolean isUvInitialized() {
        return null;
    }

    @Override
    public void setUvInitialized(boolean b) {

    }

    @Nullable
    @Override
    public Boolean isBackupEligible() {
        return null;
    }

    @Override
    public void setBackupEligible(boolean b) {

    }

    @Nullable
    @Override
    public Boolean isBackedUp() {
        return null;
    }

    @Override
    public void setBackedUp(boolean b) {

    }

    @Override
    public @NotNull AttestedCredentialData getAttestedCredentialData() {
        return new AttestedCredentialData(aaguid, credentialId, coseKey);
    }

    @Override
    public long getCounter() {
        return storedCredential.counter;
        //return 0;
    }

    @Override
    public void setCounter(long l) {
        storedCredential.counter = l;
    }
}
