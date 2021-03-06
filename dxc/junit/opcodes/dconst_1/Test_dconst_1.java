/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dxc.junit.opcodes.dconst_1;

import dxc.junit.DxTestCase;
import dxc.junit.DxUtil;
import dxc.junit.opcodes.dconst_1.jm.T_dconst_1_1;

public class Test_dconst_1 extends DxTestCase {

    /**
     * @title normal test
     */
    public void testN1() {
        T_dconst_1_1 t = new T_dconst_1_1();
        double b = 1235d;
        double c = 1234d;
        double d = b - c;
        assertEquals(d, t.run());
    }

    /**
     * @constraint 4.8.2.5
     * @title stack size
     */
    public void testVFE1() {
        try {
            Class.forName("dxc.junit.opcodes.dconst_1.jm.T_dconst_1_2");
            fail("expected a verification exception");
        } catch (Throwable t) {
            DxUtil.checkVerifyException(t);
        }
    }
}
