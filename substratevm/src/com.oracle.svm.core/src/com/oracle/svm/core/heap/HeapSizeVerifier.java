/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.heap;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;

public final class HeapSizeVerifier {
    public static void verifyHeapOptions() {
        UnsignedWord minHeapSize = WordFactory.unsigned(SubstrateGCOptions.MinHeapSize.getValue());
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        UnsignedWord maxNewSize = WordFactory.unsigned(SubstrateGCOptions.MaxNewSize.getValue());

        verifyMaxHeapSizeAgainstAddressSpace(maxHeapSize);
        verifyMinHeapSizeAgainstAddressSpace(minHeapSize);
        verifyMaxNewSizeAgainstAddressSpace(maxNewSize);
        verifyMinHeapSizeAgainstMaxHeapSize(minHeapSize);
        verifyMaxNewSizeAgainstMaxHeapSize(maxHeapSize);
    }

    public static void verifyMinHeapSizeAgainstAddressSpace(UnsignedWord minHeapSize) throws UserException {
        verifyAgainstAddressSpace(minHeapSize, "minimum heap size");
    }

    public static void verifyMaxHeapSizeAgainstAddressSpace(UnsignedWord maxHeapSize) throws UserException {
        verifyAgainstAddressSpace(maxHeapSize, "maximum heap size");
    }

    public static void verifyMaxNewSizeAgainstAddressSpace(UnsignedWord maxNewSize) {
        verifyAgainstAddressSpace(maxNewSize, "maximum new generation size");
    }

    private static void verifyAgainstAddressSpace(UnsignedWord actualValue, String actualValueName) {
        UnsignedWord addressSpaceSize = ReferenceAccess.singleton().getMaxAddressSpaceSize();
        if (actualValue.aboveThan(addressSpaceSize)) {
            throwError(actualValue, actualValueName, addressSpaceSize, "largest possible address space");
        }
    }

    private static void verifyMinHeapSizeAgainstMaxHeapSize(UnsignedWord minHeapSize) {
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && minHeapSize.aboveThan(maxHeapSize)) {
            String message = formatError(minHeapSize, MIN_HEAP_SIZE_NAME, maxHeapSize, MAX_HEAP_SIZE_NAME);
            throw reportError(message);
        }
    }

    private static void verifyMaxNewSizeAgainstMaxHeapSize(UnsignedWord maxNewSize) {
        UnsignedWord maxHeapSize = WordFactory.unsigned(SubstrateGCOptions.MaxHeapSize.getValue());
        if (maxHeapSize.notEqual(0) && maxNewSize.aboveThan(maxHeapSize)) {
            String message = formatError(maxNewSize, MAX_NEW_SIZE_NAME, maxHeapSize, MAX_HEAP_SIZE_NAME);
            throw reportError(message);
        }
    }

    private static RuntimeException reportError(String message) throws UserException {
        if (SubstrateUtil.HOSTED) {
            throw UserError.abort(message);
        }
        throw new IllegalArgumentException(message);
    }

    private static String formatError(UnsignedWord actualValue, String actualValueName, UnsignedWord maxValue, String maxValueName) {
        return "The specified " + actualValueName + " (" + format(actualValue) + ") must not be larger than the " + maxValueName + " (" + format(maxValue) + ").";
    }

    private static String format(UnsignedWord bytes) {
        String[] units = {"", "k", "m", "g", "t"};
        int index = 0;
        UnsignedWord value = bytes;
        while (value.unsignedRemainder(1024).equal(0) && index < units.length - 1) {
            value = value.unsignedDivide(1024);
            index++;
        }
        return value.rawValue() + units[index];
    }
}

@AutomaticallyRegisteredFeature
class HostedHeapSizeVerifierFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // At build-time, we can do a reasonable GC-independent verification of all the heap size
        // settings.
        HeapSizeVerifier.verifyHeapOptions();
    }
}
