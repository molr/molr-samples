/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.server.serialize;

import cern.molr.server.response.MissionIntegerResponse;
import cern.molr.server.response.MissionIntegerResponseFailure;
import cern.molr.server.response.MissionIntegerResponseSuccess;
import cern.molr.spring.util.TryResponseDeserializer;

public class MissionIntegerResponseDeserializer extends TryResponseDeserializer<MissionIntegerResponse>{

    @Override
    public Class<? extends MissionIntegerResponse> getSuccessDeserializer() {
        return MissionIntegerResponseSuccess.class;
    }

    @Override
    public Class<? extends MissionIntegerResponse> getFailureDeserializer() {
        return MissionIntegerResponseFailure.class;
    }
    
}