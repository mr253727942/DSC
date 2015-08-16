package net.floodlightcontroller.core;

import org.sdnplatform.sync.ISyncService;
import org.sdnplatform.sync.internal.config.ClusterConfig;

import com.google.common.base.Optional;

/** This class represents a unique id of this controller node. It is derived from
 *  the node id as returned by {@link ISyncService#getLocalNodeId()}.
 *  <p>
 *	这个类代表一个对应控制器节点的唯一ID,它能通过ISyncService类的getLocalNodeId方法得到
 *  Note that the unconfigured Node Id is not supported. Users are encouraged to
 *  represent an unconfigured Controller Node by {@link Optional#absent()}.
 *  备注 为配置ID的节点不被支持，鼓励使用者描述一个未配置的控制器节点通过Optional类的absent方法
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class ControllerId {
    private final short nodeId;

    private ControllerId(short nodeId) {
        if(nodeId == ClusterConfig.NODE_ID_UNCONFIGURED)
            throw new IllegalArgumentException("nodeId is unconfigured");

        this.nodeId = nodeId;
    }

    public short getNodeId() {
        return nodeId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + nodeId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ControllerId other = (ControllerId) obj;
        if (nodeId != other.nodeId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return Short.toString(nodeId);
    }

    public static ControllerId of(short nodeId) {
        return new ControllerId(nodeId);
    }

}