package brooklyn.entity.basic;

import brooklyn.entity.Effector;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;

public interface EffectorWithBody<T> extends Effector<T> {

    /** returns the body of the effector, i.e. a factory which can generate tasks which can run */
    public EffectorTaskFactory<T> getBody();
    
}