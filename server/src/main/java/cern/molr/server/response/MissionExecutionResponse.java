/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.server.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import cern.molr.server.serialize.MissionExecutionResponseDeserializer;
import cern.molr.type.trye.TryResponse;

@JsonDeserialize(using = MissionExecutionResponseDeserializer.class)
public interface MissionExecutionResponse extends TryResponse<MissionExecutionResponseBean>{

}