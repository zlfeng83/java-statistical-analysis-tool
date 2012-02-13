
package jsat.math.optimization;

import java.util.List;
import java.util.concurrent.ExecutorService;
import jsat.linear.Vec;
import jsat.math.Function;
import jsat.utils.FakeExecutor;

/**
 * This interface provides the contract for a multivariate numerical optimization method. An 
 * optimizer may be general or meant for a specific class of problems. Its goal is to find 
 * the value of the function that minimizes the prediction error. 
 * 
 * @author Edward Raff
 */
public interface Optimizer
{
    /**
     * Performs optimization on the given inputs to find the minima of the function. 
     * 
     * @param eps the desired accuracy of the result. 
     * @param iterationLimit the maximum number of iteration steps to allow
     * @param f the function to optimize
     * @param fd the derivative of the function to optimize
     * @param vars contains the initial estimate of the minima. The length should be equal to the number of variables being solved for. This value may be altered. 
     * @param inputs a list of input data point values to learn from
     * @param outputs a vector containing the true values for each data point in <tt>inputs</tt>
     * @param threadpool a source of threads to perform computation with
     * @return the compute value for the optimization. 
     */
    public Vec optimize(double eps, int iterationLimit, Function f, Function fd, Vec vars, List<Vec> inputs, Vec outputs, ExecutorService threadpool);
    
    /**
     * Performs optimization on the given inputs to find the minima of the function. 
     * 
     * @param eps the desired accuracy of the result. 
     * @param iterationLimit the maximum number of iteration steps to allow
     * @param f the function to optimize
     * @param fd the derivative of the function to optimize
     * @param vars contains the initial estimate of the minima. The length should be equal to the number of variables being solved for. This value may be altered. 
     * @param inputs a list of input data point values to learn from
     * @param outputs a vector containing the true values for each data point in <tt>inputs</tt>
     * @return the compute value for the optimization. 
     */
    public Vec optimize(double eps, int iterationLimit, Function f, Function fd, Vec vars, List<Vec> inputs, Vec outputs);
}
