/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.database.sqlite;

/**事务事件的监听器
 * A listener for transaction events.
 */
public interface SQLiteTransactionListener {
    /**事务开始后立即调用
     * Called immediately after the transaction begins.
     */
    void onBegin();

    /**事务提交前调用
     * Called immediately before commiting the transaction.
     */
    void onCommit();

    /**事务将要回滚
     * Called if the transaction is about to be rolled back.
     */
    void onRollback();
}
