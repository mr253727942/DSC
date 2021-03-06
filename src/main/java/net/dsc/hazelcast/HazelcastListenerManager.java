package net.dsc.hazelcast;



import net.dsc.hazelcast.listener.FlowMessageListener;
import net.dsc.hazelcast.message.FlowMessage;
import net.dsc.hazelcast.message.RoleMessage;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.MembershipListener;
import com.hazelcast.core.MessageListener;
//监听器管理
public class HazelcastListenerManager {
	
	private static HazelcastInstance instance = HazelcastManager.getHazelcastInstance();
	private static HazelcastInstance client = HazelcastManager.getHazelcastClient();
	
	public static void addMemberListener(MembershipListener membershipListener){//添加成员事件监听
		instance.getCluster().addMembershipListener(membershipListener);
	}
	
	/*public static void addFlowMessageListener(String TopicName){//添加流表事件监听
		ITopic<FlowMessage> topic = client.getTopic(TopicName);
		topic.addMessageListener(new FlowMessageListener());
	}
	
	public static void addListenRoleChange(String TopicName,MessageListener<RoleMessage> listener){
		ITopic<RoleMessage> topic = client.getTopic(TopicName);
		topic.addMessageListener(listener);
	}*/
	
	public   static <T>   void addMessageListener(String ToicName,MessageListener<T> messageListener ){
		ITopic<T> topic = client.getTopic(ToicName);
		topic.addMessageListener(messageListener);
	}
}
