package hex.nn;

import hex.FrameTask;
import hex.FrameTask.DataInfo;
import water.H2O;
import water.Job;
import water.Key;
import water.UKV;
import water.api.DocGen;
import water.api.NNProgressPage;
import water.api.RequestServer;
import water.fvec.Frame;
import water.util.Log;
import water.util.MRUtils;
import water.util.RString;

import java.util.Random;

import static water.util.MRUtils.sampleFrame;
import static water.util.MRUtils.sampleFrameStratified;

/**
 * NN - Neural Net implementation based on MRTask2
 */
public class NN extends Job.ValidatedJob {
  static final int API_WEAVER = 1; // This file has auto-gen'd doc & json fields
  public static DocGen.FieldDoc[] DOC_FIELDS;
  public static final String DOC_GET = "NN";

  @API(help = "Activation function", filter = Default.class, json = true)
  public Activation activation = Activation.Tanh;

  @API(help = "Hidden layer sizes (e.g. 100,100). Grid search: (10,10), (20,20,20)", filter = Default.class, json = true)
  public int[] hidden = new int[] { 200, 200 };

  @API(help = "How many times the dataset should be iterated (streamed), can be fractional", filter = Default.class, dmin = 1e-3, json = true)
  public double epochs = 10;

  @API(help = "Adaptive learning rate (AdaDelta)", filter = Default.class, json = true)
  public boolean adaptive_rate = true;

  @API(help = "Adaptive learning rate time decay factor (length of moving window over prior updates)", filter = Default.class, dmin = 0.01, dmax = 1, json = true)
  public double rho = 0.95;

  @API(help = "Adaptive learning rate smoothing factor", filter = Default.class, dmin = 1e-10, dmax = 1, json = true)
  public double epsilon = 1e-6;

  @API(help = "Learning rate (higher => less stable, lower => slower convergence)", filter = Default.class, dmin = 1e-10, dmax = 1, json = true)
  public double rate = .005;

  @API(help = "Learning rate annealing: rate / (1 + rate_annealing * samples)", filter = Default.class, dmin = 0, dmax = 1, json = true)
  public double rate_annealing = 1 / 1e6;

  @API(help = "Initial momentum at the beginning of training", filter = Default.class, dmin = 0, dmax = 0.9999999999, json = true)
  public double momentum_start = 0;

  @API(help = "Number of training samples for which momentum increases", filter = Default.class, lmin = 1, json = true)
  public long momentum_ramp = 1000000;

  @API(help = "Final momentum after the ramp is over", filter = Default.class, dmin = 0, dmax = 0.9999999999, json = true)
  public double momentum_stable = 0;

  @API(help = "Input layer dropout ratio (can improve generalization, try 0.1 or 0.2)", filter = Default.class, dmin = 0, dmax = 1, json = true)
  public double input_dropout_ratio = 0.0;

  @API(help = "L1 regularization (can add stability and improve generalization, causes many weights to become 0)", filter = Default.class, dmin = 0, dmax = 1, json = true)
  public double l1 = 0.0;

  @API(help = "L2 regularization (can add stability and improve generalization, causes many weights to be small", filter = Default.class, dmin = 0, dmax = 1, json = true)
  public double l2 = 0.0;

  @API(help = "Seed for random numbers (reproducible results for small (single-chunk) datasets only, cf. Hogwild!)", filter = Default.class, json = true)
  public long seed = new Random().nextLong();

  @API(help = "Shortest time interval (in secs) between model scoring", filter = Default.class, dmin = 0, json = true)
  public double score_interval = 5;

  @API(help = "Number of training samples after which multi-node synchronization and scoring can happen (0 for all, i.e., one epoch)", filter = Default.class, lmin = 0, json = true)
  public long mini_batch = 0l;

  @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data)", filter = Default.class, json = true, gridable = false)
  public boolean balance_classes = false;

  @API(help = "Enable expert mode (to access all options from GUI)", filter = Default.class, json = true, gridable = false)
  public boolean expert_mode = false;

  @API(help = "Maximum relative size of the training data after balancing class counts (can be less than 1.0)", filter = Default.class, json = true, dmin=1e-3, gridable = false)
  public float max_after_balance_size = 5.0f;

  @API(help = "Number of training set samples for scoring (0 for all)", filter = Default.class, lmin = 0, json = true)
  public long score_training_samples = 10000l;

  @API(help = "Number of validation set samples for scoring (0 for all)", filter = Default.class, lmin = 0, json = true)
  public long score_validation_samples = 0l;

  @API(help = "Method used to sample validation dataset for scoring", filter = Default.class, json = true, gridable = false)
  public ClassSamplingMethod score_validation_sampling = ClassSamplingMethod.Uniform;

  @API(help = "Initial Weight Distribution", filter = Default.class, json = true)
  public InitialWeightDistribution initial_weight_distribution = InitialWeightDistribution.UniformAdaptive;

  @API(help = "Uniform: -value...value, Normal: stddev)", filter = Default.class, dmin = 0, json = true)
  public double initial_weight_scale = 1.0;

  @API(help = "Loss function", filter = Default.class, json = true)
  public Loss loss = Loss.CrossEntropy;

  @API(help = "Learning rate decay factor between layers (N-th layer: rate*alpha^(N-1))", filter = Default.class, dmin = 0, json = true)
  public double rate_decay = 1.0;

  @API(help = "Constraint for squared sum of incoming weights per unit (e.g. for Rectifier)", filter = Default.class, json = true)
  public double max_w2 = Double.POSITIVE_INFINITY;

  @API(help = "Enable diagnostics for hidden layers", filter = Default.class, json = true, gridable = false)
  public boolean diagnostics = true;

  @API(help = "Maximum duty cycle fraction for scoring (lower: more training, higher: more scoring).", filter = Default.class, dmin = 0, dmax = 1, json = true)
  public double score_duty_cycle = 0.1;

  @API(help = "Enable fast mode (minor approximation in back-propagation)", filter = Default.class, json = true)
  public boolean fast_mode = true;

  @API(help = "Ignore constant training columns", filter = Default.class, json = true)
  public boolean ignore_const_cols = true;

  @API(help = "Force extra load balancing to increase training speed for small datasets (beta)", filter = Default.class, json = true)
  public boolean force_load_balance = false;

  @API(help = "Enable shuffling of training data (beta)", filter = Default.class, json = true)
  public boolean shuffle_training_data = false;

  @API(help = "Use Nesterov accelerated gradient (recommended)", filter = Default.class, json = true)
  public boolean nesterov_accelerated_gradient = true;

  @API(help = "Stopping criterion for classification error fraction (-1 to disable)", filter = Default.class, dmin=-1, dmax=1, json = true, gridable = false)
  public double classification_stop = 0;

  @API(help = "Stopping criterion for regression error (MSE) (-1 to disable)", filter = Default.class, dmin=-1, json = true, gridable = false)
  public double regression_stop = 1e-6;

  @API(help = "Enable quiet mode for less output to standard output", filter = Default.class, json = true, gridable = false)
  public boolean quiet_mode = false;

  @API(help = "Max. size (number of classes) for confusion matrices to be shown", filter = Default.class, json = true, gridable = false)
  public int max_confusion_matrix_size = 20;

  public enum ClassSamplingMethod {
    Uniform, Stratified
  }

  public enum InitialWeightDistribution {
    UniformAdaptive, Uniform, Normal
  }

  /**
   * Activation functions
   */
  public enum Activation {
    Tanh, TanhWithDropout, Rectifier, RectifierWithDropout, Maxout, MaxoutWithDropout
  }

  /**
   * Loss functions
   * CrossEntropy is recommended
   */
  public enum Loss {
    MeanSquare, CrossEntropy
  }

  @Override
  protected void registered(RequestServer.API_VERSION ver) {
    super.registered(ver);
    for (Argument arg : _arguments) {
      if ( arg._name.equals("activation") || arg._name.equals("initial_weight_distribution")
              || arg._name.equals("expert_mode") || arg._name.equals("adaptive_rate") || arg._name.equals("balance_classes")) {
        arg.setRefreshOnChange();
      }
    }
  }

  @Override protected void queryArgumentValueSet(Argument arg, java.util.Properties inputArgs) {
    super.queryArgumentValueSet(arg, inputArgs);
    if(arg._name.equals("initial_weight_scale") &&
            (initial_weight_distribution == InitialWeightDistribution.UniformAdaptive)
            ) {
      arg.disable("Using sqrt(6 / (# units + # units of previous layer)) for Uniform distribution.", inputArgs);
    }
    if(arg._name.equals("loss") && !classification) {
      arg.disable("Using MeanSquare loss for regression.", inputArgs);
      loss = Loss.MeanSquare;
    }
    if (expert_mode && arg._name.equals("force_load_balance") && H2O.CLOUD.size()>1) {
      force_load_balance = false;
      arg.disable("Only for single-node operation.");
    }

    if (classification) {
      if(arg._name.equals("regression_stop")) {
        arg.disable("Only for regression.", inputArgs);
      }
      if(arg._name.equals("max_after_balance_size") && !balance_classes) {
        arg.disable("Requires balance_classes.", inputArgs);
      }
    }
    else {
      if(arg._name.equals("classification_stop")
              || arg._name.equals("max_confusion_matrix_size")
              || arg._name.equals("max_after_balance_size")
              || arg._name.equals("balance_classes")) {
        arg.disable("Only for classification.", inputArgs);
      }
      if (validation != null && arg._name.equals("score_validation_sampling")) {
        score_validation_sampling = ClassSamplingMethod.Uniform;
        arg.disable("Using uniform sampling for validation scoring dataset.", inputArgs);
      }
    }
    if ((arg._name.equals("score_validation_samples") || arg._name.equals("score_validation_sampling")) && validation == null) {
      arg.disable("Requires a validation data set.", inputArgs);
    }
    if (arg._name.equals("loss")
            || arg._name.equals("max_w2")
            || arg._name.equals("warmup_samples")
            || arg._name.equals("score_training_samples")
            || arg._name.equals("score_validation_samples")
            || arg._name.equals("initial_weight_distribution")
            || arg._name.equals("initial_weight_scale")
            || arg._name.equals("diagnostics")
            || arg._name.equals("rate_decay")
            || arg._name.equals("score_duty_cycle")
            || arg._name.equals("fast_mode")
            || arg._name.equals("score_validation_sampling")
            || arg._name.equals("max_after_balance_size")
            || arg._name.equals("ignore_const_cols")
            || arg._name.equals("force_load_balance")
            || arg._name.equals("shuffle_training_data")
            || arg._name.equals("nesterov_accelerated_gradient")
            || arg._name.equals("classification_stop")
            || arg._name.equals("regression_stop")
            || arg._name.equals("quiet_mode")
            || arg._name.equals("max_confusion_matrix_size")
            ) {
      if (!expert_mode) arg.disable("Only in expert mode.", inputArgs);
    }

    if (!adaptive_rate) {
      if (arg._name.equals("rho") || arg._name.equals("epsilon")) {
        arg.disable("Only for adaptive learning rate.", inputArgs);
        rho = 0;
        epsilon = 0;
      }
    } else {
      if (arg._name.equals("rate") || arg._name.equals("rate_annealing") || arg._name.equals("momentum_start")
              || arg._name.equals("momentum_ramp") || arg._name.equals("momentum_stable")) {
        arg.disable("Only for non-adaptive learning rate.", inputArgs);
        momentum_start = 0;
        momentum_stable = 0;
      }
    }
  }

  public Frame score( Frame fr ) { return ((NNModel)UKV.get(dest())).score(fr);  }

  /** Print model parameters as JSON */
  @Override public boolean toHTML(StringBuilder sb) {
    return makeJsonBox(sb);
  }

  /** Return the query link to this page */
  public static String link(Key k, String content) {
    NN req = new NN();
    RString rs = new RString("<a href='" + req.href() + ".query?%key_param=%$key'>%content</a>");
    rs.replace("key_param", "source");
    rs.replace("key", k.toString());
    rs.replace("content", content);
    return rs.toString();
  }

  @Override public float progress(){
    if(UKV.get(dest()) == null)return 0;
    NNModel m = UKV.get(dest());
    if (m != null && m.model_info()!=null )
      return (float)Math.min(1, (m.epoch_counter / m.model_info().get_params().epochs));
    return 0;
  }

  @Override public JobState exec() {
    trainModel(initModel());
    delete();
    return JobState.DONE;
  }

  @Override protected Response redirect() {
    return NNProgressPage.redirect(this, self(), dest());
  }

  private boolean _fakejob;
  private void checkParams() {
    if (source.numCols() <= 1)
      throw new IllegalArgumentException("Training data must have at least 2 features (incl. response).");

    for (int i=0;i<hidden.length;++i) {
      if (hidden[i]==0)
        throw new IllegalArgumentException("Hidden layer size must be >0.");
    }

    if(!classification && loss != Loss.MeanSquare) {
      Log.warn("Setting loss to MeanSquare for regression.");
      loss = Loss.MeanSquare;
    }
    // make default job_key and destination_key in case they are missing
    if (dest() == null) {
      destination_key = Key.make();
    }
    if (self() == null) {
      job_key = Key.make();
    }
    if (UKV.get(self()) == null) {
      start_time = System.currentTimeMillis();
      state      = JobState.RUNNING;
      UKV.put(self(), this);
      _fakejob = true;
    }
  }

  /**
   * Create an initial NN model, typically to be trained by trainModel(model)
   * @return Randomly initialized model
   */
  public final NNModel initModel() {
    try {
      lock_data();
      checkParams();
      final Frame train = FrameTask.DataInfo.prepareFrame(source, response, ignored_cols, classification, ignore_const_cols);
      final DataInfo dinfo = new FrameTask.DataInfo(train, 1, true, !classification);
      float[] priorDist = classification ? new MRUtils.ClassDist(dinfo._adaptedFrame.lastVec()).doAll(dinfo._adaptedFrame.lastVec()).rel_dist() : null;
      final NNModel model = new NNModel(dest(), self(), source._key, dinfo, this, priorDist);
      model.model_info().initializeMembers();
      return model;
    }
    finally {
      unlock_data();
    }
  }

  /**
   * Train a NN model
   * @param model Input model (e.g., from initModel(), or from a previous training run)
   * @return Trained model
   */
  public final NNModel trainModel(NNModel model) {
    Frame[] valid_adapted = null;
    Frame valid = null, validScoreFrame = null;
    Frame train = null, trainScoreFrame = null;
    try {
      lock_data();
      logStart();
      if (model == null) {
        model = UKV.get(dest());
      }
      model.write_lock(self());
      final long model_size = model.model_info().size();
      Log.info("Number of model parameters (weights/biases): " + String.format("%,d", model_size));
//      Log.info("Memory usage of the model: " + String.format("%.2f", (double)model_size*Float.SIZE / (1<<23)) + " MB.");
      train = model.model_info().data_info()._adaptedFrame;
      train = reBalance(train, seed);
      float[] trainSamplingFactors;
      if (classification && balance_classes) {
        trainSamplingFactors = new float[train.lastVec().domain().length]; //leave initialized to 0 -> will be filled up below
        train = sampleFrameStratified(train, train.lastVec(), trainSamplingFactors, (long)(max_after_balance_size*train.numRows()), seed, true, false);
        model.setModelClassDistribution(new MRUtils.ClassDist(train.lastVec()).doAll(train.lastVec()).rel_dist());
      }
      trainScoreFrame = sampleFrame(train, score_training_samples, seed); //training scoring dataset is always sampled uniformly from the training dataset

      Log.info("Number of chunks of the training data: " + train.anyVec().nChunks());
      if (validation != null) {
        valid_adapted = model.adapt(validation, false);
        valid = reBalance(valid_adapted[0], seed+1); //rebalance for load balancing, shuffle for "fairness"
        // validation scoring dataset can be sampled in multiple ways from the given validation dataset
        if (classification && balance_classes && score_validation_sampling == ClassSamplingMethod.Stratified) {
          validScoreFrame = sampleFrameStratified(valid, valid.lastVec(), null,
                  score_validation_samples > 0 ? score_validation_samples : valid.numRows(), seed+1, false /* no oversampling */, false);
        } else {
          validScoreFrame = sampleFrame(valid, score_validation_samples, seed+1);
        }
        Log.info("Number of chunks of the validation data: " + valid.anyVec().nChunks());
      }
      model.training_rows = train.numRows();
      if (mini_batch > train.numRows()) {
        Log.warn("Setting mini_batch (" + mini_batch
                + ") to the number of rows of the training data (" + (mini_batch=train.numRows()) + ").");
      }
      // determines the number of rows processed during NNTask, affects synchronization (happens at the end of each NNTask)
      final float sync_fraction = mini_batch == 0l ? 1.0f : (float)mini_batch / train.numRows();

      if (!quiet_mode) Log.info("Initial model:\n" + model.model_info());

      Log.info("Starting to train the Neural Net model.");
      long timeStart = System.currentTimeMillis();

      //main loop
      do model.set_model_info(new NNTask(model.model_info(), sync_fraction).doAll(train).model_info());
      while (model.doScoring(train, trainScoreFrame, validScoreFrame, timeStart, self()));

      Log.info("Finished training the Neural Net model.");
      return model;
    }
    catch(Exception ex) {
      ex.printStackTrace();
      throw new RuntimeException(ex);
    }
    finally {
      if (model != null) model.unlock(self());
      if (validScoreFrame != null && validScoreFrame != valid) validScoreFrame.delete();
      if (trainScoreFrame != null && trainScoreFrame != train) trainScoreFrame.delete();
      if (validation != null && valid_adapted != null && valid_adapted.length > 1 ) valid_adapted[1].delete(); //just deleted the adapted frames for validation
      unlock_data();
    }
  }

  /**
   * Lock the input datasets against deletes
   */
  private void lock_data() {
    source.read_lock(self());
    if( validation != null && source._key != null && validation._key !=null && !source._key.equals(validation._key) )
      validation.read_lock(self());
  }

  /**
   * Release the lock for the input datasets
   */
  private void unlock_data() {
    source.unlock(self());
    if( validation != null && !source._key.equals(validation._key) )
      validation.unlock(self());
  }

  /**
   * Delete job related keys
   */
  public void delete() {
    if (_fakejob) UKV.remove(job_key);
    remove();
  }

  /**
   * Rebalance a frame for load balancing
   * @param fr Input frame
   * @param seed RNG seed
   * @return Frame that can be load-balanced (and shuffled), depending on whether force_load_balance and shuffle_training_data are set
   */
  private Frame reBalance(final Frame fr, long seed) {
    return force_load_balance || shuffle_training_data ? MRUtils.shuffleAndBalance(fr, seed, shuffle_training_data) : fr;
  }

}
