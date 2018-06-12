/**
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package cern.molr.commons.api.type.trye;

import cern.molr.commons.api.type.either.Left;

/**
 * Refer to {@link Try}
 *
 * @param <T>
 *
 * @author nachivpn
 * @author yassine-kr
 */
public class Failure<T> extends Left<Throwable, T> implements Try<T> {

    public Failure(Throwable throwable) {
        super(throwable);
    }

}