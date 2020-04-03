package ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import context.Context;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

public class ContextSetPlotter extends Application {

	private static String agName = "";
	private static List<Context> contexts;

	@Override
	public void start(Stage stage) {
		stage.setTitle(agName);
		final NumberAxis xAxis = new NumberAxis();
		final NumberAxis yAxis = new NumberAxis();
		final LineChart<Number, Number> lineChart = new LineChart<Number, Number>(xAxis, yAxis);
		lineChart.setCreateSymbols(false);
		lineChart.setLegendVisible(false);

		lineChart.getYAxis().setAutoRanging(true);
		((NumberAxis) lineChart.getYAxis()).setForceZeroInRange(false);
		((NumberAxis) lineChart.getXAxis()).setForceZeroInRange(false);

		Map<Integer, Integer> sizeCount = new HashMap<>();

		for (int i = 0; i < contexts.size(); i++) {
			XYChart.Series<Number, Number> series = new XYChart.Series<>();
			double[] situation = contexts.get(i).asDoubleArray();
			for (int j = 0; j < situation.length; j++) {

				int idx = contexts.get(i).getFinalDataIdx() - situation.length + j;
				if (idx >= 0) {
					series.getData().add(new XYChart.Data<>(idx, i));
				}
			}

			if (!sizeCount.containsKey(situation.length)) {
				sizeCount.put(situation.length, 1);
			} else {
				sizeCount.put(situation.length, sizeCount.get(situation.length) + 1);
			}

			lineChart.getData().add(series);
		}

		Scene scene = new Scene(lineChart, 800, 600);
		stage.setScene(scene);
		stage.show();
	}

	public static void run(List<Context> contexts, String agName) {
		ContextSetPlotter.agName = agName;
		ContextSetPlotter.contexts = new ArrayList<Context>(contexts);

		ContextSetPlotter.contexts.sort((c1, c2) -> Integer.compare(c1.getFinalDataIdx(), c2.getFinalDataIdx()));

		launch(new String[] {});
	}
}