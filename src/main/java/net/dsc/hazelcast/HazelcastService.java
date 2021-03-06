package net.dsc.hazelcast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.dsc.cluster.web.FlowEntryPushUtil;
import net.dsc.hazelcast.listener.FlowMessageListener;
import net.dsc.hazelcast.listener.RoleMessageListener;
import net.dsc.hazelcast.listener.ShutDownMessageListener;
import net.dsc.hazelcast.message.FlowMessage;
import net.dsc.hazelcast.message.RoleMessage;
import net.dsc.hazelcast.message.ShutDownMessage;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticflowentry.StaticFlowEntryPusher;
import net.floodlightcontroller.storage.IStorageSourceService;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Member;
import com.hazelcast.core.MembershipListener;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import com.hazelcast.core.MultiMap;

public class HazelcastService implements IHazelcastService,IFloodlightModule,IMessageListener{
	private  static final String FlowMessageTopic = "flowMessageTopic";
	private static final String STR_ROLE_MASTER = "MASTER"; 
	
	private static final String STR_ROLE_SLAVE = "SLAVE";
	private static final String STR_ROLE_EQUAL = "EQUAL";
	private static final String STR_ROLE_OTHER = "OTHER";
	
	private static final Logger log = LoggerFactory
			.getLogger(HazelcastService.class);

	private HazelcastInstance hazelcastInstance = null;
	private HazelcastInstance client = null;
	private static IStorageSourceService storageSourceService = null;
	private static IOFSwitchService switchService = null;
	private static IShutdownService shutService=null;

	@Override
	public <K, V> IMap<K,V> getMap(String MapName) {
		IMap<K, V> map = hazelcastInstance.getMap(MapName);
		return map;
	}
	@Override
	public <K, V> MultiMap<K,V> getMultiMap(String MapName) {
		MultiMap<K, V> map = client.getMultiMap(MapName);
		return map;
	}
	@Override
	public <T> ISet<T> getSet(String SetName) {
		ISet<T> set = client.getSet(SetName);
		return set;
	}

	@Override
	public <T> IQueue<T> getQueue(String QueueName) {
		IQueue<T> queue = client.getQueue(QueueName);
		return queue;
	}

	@Override
	public <T> IList<T> getList(String ListName) {
		IList<T> list = client.getList(ListName);
		return list;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IHazelcastService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IHazelcastService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOFSwitchService.class);
		l.add(IStorageSourceService.class);
		l.add(IShutdownService.class);
		return l;
	}
	//test
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		log.info("Hazelcast Init");
		switchService = context.getServiceImpl(IOFSwitchService.class);
		storageSourceService = context.getServiceImpl(IStorageSourceService.class);
		hazelcastInstance = HazelcastManager.getHazelcastInstance();
		shutService=context.getServiceImpl(IShutdownService.class);
		client = HazelcastManager.getHazelcastClient();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
/*		HazelcastListenerManager.addFlowMessageListener(FlowMessageTopic);
		HazelcastListenerManager.addListenRoleChange(getLocalMember().getUuid(),new FlowM);*/
		
		HazelcastListenerManager.addMessageListener(getLocalMember().getUuid(), new RoleMessageListener(this));
		HazelcastListenerManager.addMessageListener(getLocalMember().getUuid()+"flow", new FlowMessageListener(this));
		HazelcastListenerManager.addMessageListener(getLocalMember().getUuid()+"shutdown", new ShutDownMessageListener(this));
		
	}
	
	@Override
	public void publishFlowMessage(FlowMessage flowMessage,String ControllerId) {
		ITopic<FlowMessage> topic =  client.getTopic(ControllerId+"flow");
		topic.publish(flowMessage);
	}
	
	@Override
	public void publishRoleMessage(RoleMessage roleMessage,String ControllerId) {
		ITopic<RoleMessage> topic = client.getTopic(ControllerId);
		topic.publish(roleMessage);
	}
	
	@Override
	public void publishShutMessage(ShutDownMessage shutmessage,
			String controllerId) {
		ITopic<ShutDownMessage> topic = client.getTopic(controllerId+"shutdown");
		topic.publish(shutmessage);
	}
	
	@Override
	public Member getLocalMember() {
		return hazelcastInstance.getCluster().getLocalMember();
		
	}
	
	@Override
	public void addMemberListener(MembershipListener mebershipListener) {
		HazelcastListenerManager.addMemberListener(mebershipListener);	
	}
	

	
	

	private static OFControllerRole parseRole(String role) {
		if (role == null || role.isEmpty()) {
			return OFControllerRole.ROLE_NOCHANGE;
		}

		role = role.toUpperCase();

		if (role.contains(STR_ROLE_MASTER)) {
			return OFControllerRole.ROLE_MASTER;
		} else if (role.contains(STR_ROLE_SLAVE)) {
			return OFControllerRole.ROLE_SLAVE;
		} else if (role.contains(STR_ROLE_EQUAL)
				|| role.contains(STR_ROLE_OTHER)) {
			return OFControllerRole.ROLE_EQUAL;
		} else {
			return OFControllerRole.ROLE_NOCHANGE;
		}
	}
	@Override
	public void progressRoleMessage(Message<RoleMessage> message) {//处理角色请求订阅消息
		RoleMessage roleMessage = message.getMessageObject();
		String switchId = roleMessage.SwitchId;
		DatapathId dpid = DatapathId.of(switchId);// 得到请求交换机机id

		IOFSwitch sw = switchService.getSwitch(dpid);// 得到交换机
		OFControllerRole controllerRole = parseRole(roleMessage.Role);
		sw.writeRequest(sw.getOFFactory()
				.buildRoleRequest()
				.setGenerationId(U64.ZERO)
				.setRole(controllerRole).
				build());
		
	}
	@Override
	public void progressFlowMessage(Message<FlowMessage> Message) {//处理流表订阅消息
		FlowMessage flowMessage = Message.getMessageObject();
		Map<String,Object> rawValues = new HashMap<String,Object>();
		
		String json = flowMessage.json;
		System.out.println(json);
		try {
			rawValues = FlowEntryPushUtil.jsonToStorageEntry(json);
			int state = FlowEntryPushUtil.checkFlow(rawValues);
			if(state == 0 ){
				storageSourceService.insertRowAsync(StaticFlowEntryPusher.TABLE_NAME, rawValues);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void progressShutDownMessage(Message<ShutDownMessage> shutMessage) {
		log.info("shutdown DSC");
		getInstance().shutdown();
		shutService.terminate("shutdown", 0);
	}
	
	@Override
	public HazelcastInstance getInstance() {
		return HazelcastManager.getHazelcastInstance();
	};
}
	

