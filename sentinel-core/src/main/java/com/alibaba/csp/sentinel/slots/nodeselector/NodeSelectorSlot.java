/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.nodeselector;

import com.alibaba.csp.sentinel.Constants;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.context.ContextUtil;
import com.alibaba.csp.sentinel.node.ClusterNode;
import com.alibaba.csp.sentinel.node.DefaultNode;
import com.alibaba.csp.sentinel.node.EntranceNode;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.spi.Spi;

import java.util.HashMap;
import java.util.Map;

/**
 * 尝试构建调用跟踪的{@link com.alibaba.csp.sentinel.slotchain.ProcessorSlot}。
 * <ol>
 *     <li>如果需要添加一个新的{@link DefaultNode}作为上下文的最后一个子节点。
 *     上下文的最后一个节点是当前节点或上下文的父节点。</li>
 *     <li>将自身设置为上下文的当前节点</li>
 * </ol>
 *
 * <p>工作流程如下：
 * <pre>{@code
 *  ContextUtil.enter("entrance1", "appA");
 *  Entry nodeA = SphU.entry("nodeA");
 *  if (nodeA != null) {
 *      nodeA.exit();
 *  }
 *  ContextUtil.exit();
 * }</pre>
 *
 * <p>上述代码将在内存中生成如下的调用结构：
 * <pre>
 *
 *              machine-root
 *                  /
 *                 /
 *           EntranceNode1
 *               /
 *              /
 *        DefaultNode(nodeA)- - - - - -> ClusterNode(nodeA);
 * </pre>
 *
 * <p>此处的{@code EntranceNode}代表了{@code ContextUtil#enter("entrance1", "appA")}提供的"entrance1"。
 *
 * <p>所有的DefaultNode(nodeA)和ClusterNode(nodeA)都持有"nodeA"的统计信息，
 * 该信息由{@code SphU.entry("nodeA")}。
 *
 * <p>{@link ClusterNode}由资源ID进行唯一标识；{@link DefaultNode}由资源ID和{@link Context}唯一标识。
 * 换句话说，一个资源ID将为每个不同的上下文生成多个{@link DefaultNode}，但只会生成一个{@link ClusterNode}。
 *
 * <p>以下代码展示了同一个资源ID在两种不同上下文中的示例：
 * <pre>{@code
 *  ContextUtil.enter("entrance1", "appA");
 *  Entry nodeA = SphU.entry("nodeA");
 *  if (nodeA != null) {
 *      nodeA.exit();
 *  }
 *  ContextUtil.exit();
 *
 *  ContextUtil.enter("entrance2", "appA");
 *  nodeA = SphU.entry("nodeA");
 *  if (nodeA != null) {
 *      nodeA.exit();
 *  }
 *  ContextUtil.exit();
 * }</pre>
 *
 * <p>以上代码将在内存中生成如下调用结构：
 * <pre>
 *
 *                  machine-root
 *                  /         \
 *                 /           \
 *         EntranceNode1   EntranceNode2
 *               /               \
 *              /                 \
 *      DefaultNode(nodeA)   DefaultNode(nodeA)
 *             |                    |
 *             +- - - - - - - - - - +- - - - - - -> ClusterNode(nodeA);
 * </pre>
 *
 * <p>因此我们可以看到，在两个上下文中为"nodeA"创建了两个{@link DefaultNode}，
 * 但是只创建了一个{@link ClusterNode}。
 *
 * <p>我们也可以通过调用{@code http://localhost:8719/tree?type=root}来检查结构。
 *
 * @author jialiang.linjl
 * @see EntranceNode
 * @see ContextUtil
 */
@Spi(isSingleton = false, order = Constants.ORDER_NODE_SELECTOR_SLOT)
public class NodeSelectorSlot extends AbstractLinkedProcessorSlot<Object> {

    /**
     * 相同资源在不同上下文的{@link DefaultNode}。
     *
     * <p>需要注意的是，我们使用context name而不是resource name作为键。
     *
     * <p>需要记住相同资源将全局共享相同的{@link com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain}，
     * 无论是在哪一个上下文中。因此当进入到{@link #entry(Context, ResourceWrapper, Object, int, boolean, Object...)}时，
     * 资源名称肯定相同，但是上下文名称可能不同。
     *
     * <p>如果我们使用{@link com.alibaba.csp.sentinel.SphU#entry(String)}进入到不同上下文的相同资源，
     * 使用上下文名称作为key可以区分相同资源。在这种场景下，会使用相同资源名称为不同上下文创建多个{@link DefaultNode}。
     *
     * <p>考虑另一个问题，一个资源可能存在多个{@link DefaultNode}，因此最快获取相同资源的累计统计信息的方式是什么？
     * 答案就是所有具有相同资源名称的{@link DefaultNode}共享同一个{@link ClusterNode}。
     * 查看{@link com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot}进一步获取信息。
     *
     */
    private volatile Map<String/* context name */, DefaultNode> map = new HashMap<String, DefaultNode>(10);

    @Override
    public void entry(Context context, ResourceWrapper resourceWrapper, Object obj, int count, boolean prioritized, Object... args) throws Throwable {
        // 获取该资源的默认节点
        DefaultNode node = map.get(context.getName());
        if (node == null) {
            synchronized (this) {
                // 获取该资源你的默认节点
                node = map.get(context.getName());
                if (node == null) {
                    // 创建新节点
                    node = new DefaultNode(resourceWrapper, null);
                    // 保存到缓存中
                    HashMap<String, DefaultNode> cacheMap = new HashMap<String, DefaultNode>(map.size());
                    cacheMap.putAll(map);
                    cacheMap.put(context.getName(), node);
                    map = cacheMap;
                    // 构建调用树
                    ((DefaultNode) context.getLastNode()).addChild(node);
                }

            }
        }

        // 将新节点更新为上下文中当前节点
        context.setCurNode(node);
        // entry完成
        fireEntry(context, resourceWrapper, node, count, prioritized, args);
    }

    @Override
    public void exit(Context context, ResourceWrapper resourceWrapper, int count, Object... args) {
        // exit完成
        fireExit(context, resourceWrapper, count, args);
    }
}
