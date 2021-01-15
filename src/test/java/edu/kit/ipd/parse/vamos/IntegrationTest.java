package edu.kit.ipd.parse.vamos;

import edu.kit.ipd.parse.contextanalyzer.ContextAnalyzer;
import edu.kit.ipd.parse.corefanalyzer.CorefAnalyzer;
import edu.kit.ipd.parse.graphBuilder.GraphBuilder;
import edu.kit.ipd.parse.luna.data.MissingDataException;
import edu.kit.ipd.parse.luna.data.PrePipelineData;
import edu.kit.ipd.parse.luna.graph.IGraph;
import edu.kit.ipd.parse.luna.pipeline.PipelineStageException;
import edu.kit.ipd.parse.luna.tools.StringToHypothesis;
import edu.kit.ipd.parse.ner.NERTagger;
import edu.kit.ipd.parse.shallownlp.ShallowNLP;
import edu.kit.ipd.parse.srlabeler.SRLabeler;
import edu.kit.ipd.parse.teaching.TeachingDetector;
import org.junit.BeforeClass;
import org.junit.Test;

public class IntegrationTest {
	private static GraphBuilder graphBuilder;
	private static ShallowNLP snlp;
	private static SRLabeler srLabeler;
	private static NERTagger nerTagger;
	private static ContextAnalyzer contextAnalyzer;
	private static PrePipelineData ppd;
	private static CorefAnalyzer coref;
	private static MyTeachingDetector teachingDetector;
	private static MethodSynthesizer methodSynthesizer;

	@BeforeClass
	public static void setUp() {

		ppd = new PrePipelineData();

		snlp = new ShallowNLP();
		snlp.init();

		nerTagger = new NERTagger();
		nerTagger.init();

		srLabeler = new SRLabeler();
		srLabeler.init();

		contextAnalyzer = new ContextAnalyzer();
		contextAnalyzer.init();

		coref = new CorefAnalyzer();
		coref.init();

		graphBuilder = new GraphBuilder();
		graphBuilder.init();

		teachingDetector = new MyTeachingDetector();
		teachingDetector.init();

		methodSynthesizer = new MethodSynthesizer();
		methodSynthesizer.init();
	}

	@Test
	public void testSimpleUtterance() {
		String input = "open the microwave door";
		ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input, true));
		executeFullPrePipe(ppd);
		try {
			methodSynthesizer.setGraph(executeMandatoryAgentsOnce(ppd));
		} catch (MissingDataException e) {
			e.printStackTrace();
		}
		methodSynthesizer.exec();
		System.out.println(methodSynthesizer.getGraph());
	}

	@Test
	public void testCorefUtterance() {
		String input = "locate the table and move towards it";
		ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input, true));
		executeFullPrePipe(ppd);
		try {
			methodSynthesizer.setGraph(executeMandatoryAgentsOnce(ppd));
		} catch (MissingDataException e) {
			e.printStackTrace();
		}
		methodSynthesizer.exec();
		System.out.println(methodSynthesizer.getGraph());
	}

	@Test
	public void testComplexCorefUtterance() {
		String input = "go to the table locate the microwave and open its door";
		ppd.setMainHypothesis(StringToHypothesis.stringToMainHypothesis(input, true));
		executeFullPrePipe(ppd);
		try {
			methodSynthesizer.setGraph(executeMandatoryAgentsMultiple(ppd));
		} catch (MissingDataException e) {
			e.printStackTrace();
		}
		methodSynthesizer.exec();
		System.out.println(methodSynthesizer.getGraph());
	}

	private void executeFullPrePipe(PrePipelineData ppd) {
		try {
			snlp.exec(ppd);
			nerTagger.exec(ppd);
			srLabeler.exec(ppd);
			graphBuilder.exec(ppd);
		} catch (PipelineStageException e) {
			e.printStackTrace();
		}
	}

	private IGraph executeMandatoryAgentsOnce(PrePipelineData ppd) throws MissingDataException {
		contextAnalyzer.setGraph(ppd.getGraph());
		contextAnalyzer.exec();
		coref.setGraph(contextAnalyzer.getGraph());
		coref.exec();
		teachingDetector.setGraph(coref.getGraph());
		teachingDetector.exec();
		return teachingDetector.getGraph();
	}

	private IGraph executeMandatoryAgentsMultiple(PrePipelineData ppd) throws MissingDataException {
		contextAnalyzer.setGraph(ppd.getGraph());
		contextAnalyzer.exec();
		coref.setGraph(contextAnalyzer.getGraph());
		coref.exec();
		contextAnalyzer.setGraph(coref.getGraph());
		contextAnalyzer.exec();
		coref.setGraph(contextAnalyzer.getGraph());
		coref.exec();
		contextAnalyzer.setGraph(coref.getGraph());
		contextAnalyzer.exec();
		coref.setGraph(contextAnalyzer.getGraph());
		coref.exec();
		teachingDetector.setGraph(coref.getGraph());
		teachingDetector.exec();
		return teachingDetector.getGraph();
	}

	static class MyTeachingDetector extends TeachingDetector {
		@Override
		protected void exec() {
			super.exec();
		}
	}
}
