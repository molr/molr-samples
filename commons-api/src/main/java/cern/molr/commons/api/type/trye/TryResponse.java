/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.commons.api.type.trye;

/**
 * {@link TryResponse} is bean-ified Try
 * It offers getters and setters (which may return null or be ineffective respectively),
 * but is easy to serialize and might be preferred by some for programming
 *
 * @param <T>
 *
 * @author nachivpn
 * @author yassine-kr
 */
public interface TryResponse<T> extends Try<T> {
    Throwable getThrowable();

    T getResult();
}