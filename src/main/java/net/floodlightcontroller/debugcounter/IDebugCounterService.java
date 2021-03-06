package net.floodlightcontroller.debugcounter;

import java.util.List;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IDebugCounterService extends IFloodlightService {
    public enum MetaData {
        WARN,
        DROP,
        ERROR
    }

    /**
     * All modules that wish to have the DebugCounterService count for them, must
     * register themselves. If a module is registered multiple times subsequent
     * registrations will reset all counter in the module.
     *	所有希望拥有DebugCounterService的模块必须注册。
     *	如果模块在多个时间被注册，随后的注册将会重置模块中所有的计数器
     * @param moduleName
     * @return true if the module is registered for the first time, false if
     * the modue was previously registered
     * 当模块第一次注册时返回true，否则返回false
     */
    public boolean registerModule(String moduleName);

    /**
     * All modules that wish to have the DebugCounterService count for them, must
     * register their counters by making this call (typically from that module's
     * 'startUp' method). The counter can then be updated, displayed, reset etc.
     * using the registered moduleName and counterHierarchy.
     *	所有希望拥有DebugCounterService的模块必须注册他们的计数器通过调用这个方法(通常从模块的startup方法)。
     *	计数器之后能被更新，显示，重置等
     *	使用moduleName和conterHierarchy
     * @param moduleName           the name of the module which is registering the
     *                             counter eg. linkdiscovery or controller or switch
     *                             哪一个模块注册的计数器
     * @param counterHierarchy     the hierarchical counter name specifying all
     *                             the hierarchical levels that come above it.
     *                             For example: to register a drop counter for
     *                             packet-ins from a switch, the counterHierarchy
     *                             can be "00:00:00:00:01:02:03:04/pktin/drops"
     *                             It is necessary that counters in hierarchical levels
     *                             above have already been pre-registered - in this
     *                             example: "00:00:00:00:01:02:03:04/pktin" and
     *                             "00:00:00:00:01:02:03:04"
     *                             
     * @param counterDescription   a descriptive string that gives more information
     *                             of what the counter is measuring. For example,
     *                             "Measures the number of incoming packets seen by
     *                             this module".
     * @param metaData             variable arguments that qualify a counter
     *                             eg. warn, error etc.
     * @return                     IDebugCounter with update methods that can be
     *                             used to update a counter.
     */
    public IDebugCounter
    registerCounter(String moduleName, String counterHierarchy,
                    String counterDescription, MetaData... metaData);


    /**
     * Resets the value of counters in the hierarchy to zero. Note that the reset
     * applies to the level of counter hierarchy specified AND ALL LEVELS BELOW it
     * in the hierarchy.
     * 重置控制器层次
     * 备注：重置适用于指定的计数器层次或所有的低于这个层次的计数器
     * For example: If a hierarchy exists like "00:00:00:00:01:02:03:04/pktin/drops"
     *              specifying a reset hierarchy: "00:00:00:00:01:02:03:04"
     *              will reset all counters for the switch dpid specified;
     *              while specifying a reset hierarchy: ""00:00:00:00:01:02:03:04/pktin"
     *              will reset the pktin counter and all levels below it (like drops)
     *              for the switch dpid specified.
     * @return false if the given moduleName, counterHierarchy
     * does not exist
     */
    public boolean resetCounterHierarchy(String moduleName, String counterHierarchy);

    /**
     * Resets the values of all counters in the system.
     * 重置所有计数器
     */
    public void resetAllCounters();

    /**
     * Resets the values of all counters belonging
     * to a module with the given 'moduleName'.
     * 重置属于指定模块的所有计数器
     * @return false if the given module name does not exists
     */
    public boolean resetAllModuleCounters(String moduleName);
    
    /**
     * Removes/deletes the counter hierarchy AND ALL LEVELS BELOW it in the hierarchy.
     * 移除/删除指定的计数器层次和低于此层次的计数器。
     * For example: If a hierarchy exists like "00:00:00:00:01:02:03:04/pktin/drops"
     *              specifying a remove hierarchy: "00:00:00:00:01:02:03:04"
     *              will remove all counters for the switch dpid specified;
     *              while specifying a remove hierarchy: ""00:00:00:00:01:02:03:04/pktin"
     *              will remove the pktin counter and all levels below it (like drops)
     *              for the switch dpid specified.
     * @return false if the given moduleName, counterHierarchy does not exist
     */
    public boolean removeCounterHierarchy(String moduleName, String counterHierarchy);


    /**
     * Get counter value and associated information for the specified counterHierarchy.
     * Note that information on the level of counter hierarchy specified
     * AND ALL LEVELS BELOW it in the hierarchy will be returned.
     * 得到计数器值和相应的信息从指定的层次中
     * 备注指定层级的信息和所有低于这个层级将会被返回
     * For example,
     * if a hierarchy exists like "00:00:00:00:01:02:03:04/pktin/drops", then
     * specifying a counterHierarchy of "00:00:00:00:01:02:03:04/pktin" in the
     * get call will return information on the 'pktin' as well as the 'drops'
     * counters for the switch dpid specified.
     *
     * If the module or hierarchy is not registered, returns an empty list
     *
     * @return A list of DebugCounterResource or an empty list if the counter
     *         could not be found
     */
    public List<DebugCounterResource>
    getCounterHierarchy(String moduleName, String counterHierarchy);

    /**
     * Get counter values and associated information for all counters in the
     * system
     *	得到所有的计数器值
     * @return the list of values/info or an empty list
     */
    public List<DebugCounterResource> getAllCounterValues();

    /**
     * Get counter values and associated information for all counters associated
     * with a module.
     * If the module is not registered, returns an empty list
     *	得到所有的模块计数器值
     * @param moduleName
     * @return the list of values/info or an empty list
     */
    public  List<DebugCounterResource> getModuleCounterValues(String moduleName);

}
