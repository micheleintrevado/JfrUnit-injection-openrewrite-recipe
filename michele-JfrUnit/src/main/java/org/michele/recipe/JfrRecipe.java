package org.michele.recipe;

import java.util.Comparator;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class JfrRecipe extends Recipe {

	// TODO DA RIVEDERE: capire in quale classe iniettare JfrUnit
//	@Option(displayName = "Fully Qualified Class Name", description = "A fully qualified class name indicating which class to add JfrUnit code to.", example = "org.apache.logging.log4j.core.lookup.Log4jLookupTest")
//	@NonNull
//	String fullyQualifiedClassName;

//	@JsonCreator
//	public JfrRecipe(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
//		this.fullyQualifiedClassName = fullyQualifiedClassName;
//	}

	@JsonCreator
	public JfrRecipe() {

	}

	@Nullable
	@Override
	protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
		return new UsesType<>("org.junit.jupiter.api.Test", false);
	}

	@Override
	public String getDisplayName() {
		return "JfrRecipe";
	}

	@Override
	public String getDescription() {
		return "custom OpenRewrite recipe used to inject JfrUnit in JUnit 5 test.";
	}

	@Override
	protected JavaIsoVisitor<ExecutionContext> getVisitor() {
		return new JfrUnitVisitor();
	}

	public class JfrUnitVisitor extends JavaIsoVisitor<ExecutionContext> {

		private String classAnnotationImport = "org.moditect.jfrunit.JfrEventTest";
		private String jfrEventsVariableImport = "org.moditect.jfrunit.JfrEvents";
		private String jfrAnnotationImport = "org.moditect.jfrunit.EnableEvent";

		private final JavaTemplate jfrUnitClassAnnotationTemplate = JavaTemplate
				.builder(this::getCursor, "@JfrEventTest").imports(classAnnotationImport).build();

		private final JavaTemplate jfrEventsVariableTemplate = JavaTemplate
				.builder(this::getCursor, "public JfrEvents jfrEvents = new JfrEvents();")
				.imports(jfrEventsVariableImport).build();

		private final JavaTemplate jfrUnitMethodAnnotationTemplate = JavaTemplate
				.builder(this::getCursor, "@EnableEvent(\"jdk.*\")").imports(jfrAnnotationImport).build();

		private final JavaTemplate addAwaitEventsTemplate = JavaTemplate
				.builder(this::getCursor, "jfrEvents.awaitEvents();").build();

		// VISIT PARTS OF THE LST

		// add @JfrEventTest annotation and its import before the class
		@Override
		public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl,
				ExecutionContext executionContext) {

			classDecl = super.visitClassDeclaration(classDecl, executionContext);

//			System.out.println(TreeVisitingPrinter.printTree(getCursor()));

			// check if the class already has a @JfrEventTest annotation. Don't make any
			// changes if there is the @JfrEventTest annotation.
			boolean hasJfrUnitClassAnnotation = classDecl.getLeadingAnnotations().stream()
					.anyMatch(jfrClassAnnotation -> jfrClassAnnotation.getSimpleName().equals("JfrEventTest"));
			if (!hasJfrUnitClassAnnotation) {
				classDecl = classDecl.withTemplate(jfrUnitClassAnnotationTemplate,
						classDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
				maybeAddImport("import org.moditect.jfrunit.JfrEventTest;");
			}

			if (classDecl.getBody() != null) {
				boolean hasJfrEventsVariable = classDecl.getBody().getStatements().stream()
						.anyMatch(variableStatement -> variableStatement.toString()
								.equals("public JfrEvents jfrEvents = new JfrEvents()"));
				if (!hasJfrEventsVariable) {
					classDecl = classDecl.withTemplate(jfrEventsVariableTemplate,
							classDecl.getBody().getCoordinates().firstStatement());
					maybeAddImport("import org.moditect.jfrunit.JfrEvents;");
				}
			}
			return classDecl;
		}

		@Override
		public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl,
				ExecutionContext executionContext) {

			methodDecl = super.visitMethodDeclaration(methodDecl, executionContext);

			// check if the method already has an @EnableEvent("jdk.*") annotation.
			boolean hasJfrUnitMethodAnnotation = methodDecl.getLeadingAnnotations().stream()
					.anyMatch(jfrMethodAnnotation -> jfrMethodAnnotation.getSimpleName().equals("EnableEvent"));

			// check if the method has @Test annotation
			boolean hasTestAnnotation = methodDecl.getLeadingAnnotations().stream()
					.anyMatch(methodAnnotation -> methodAnnotation.getSimpleName().equals("Test"));
			if ((!hasJfrUnitMethodAnnotation) && hasTestAnnotation) {
				methodDecl = methodDecl.withTemplate(jfrUnitMethodAnnotationTemplate,
						methodDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
				maybeAddImport("import org.moditect.jfrunit.EnableEvent;");

				if (methodDecl.getBody() != null) {
					boolean hasJfrUnitAwaitEventsStatement = methodDecl.getBody().getStatements().stream()
							.anyMatch(methodStatement -> methodStatement.toString().equals("jfrEvents.awaitEvents()"));
					if ((!hasJfrUnitAwaitEventsStatement) && hasTestAnnotation) {
						methodDecl = methodDecl.withTemplate(addAwaitEventsTemplate,
								methodDecl.getBody().getCoordinates().lastStatement());
					}
				}
			}

			return methodDecl;
		}

//		@Override
//		public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variableDeclarations,
//				ExecutionContext executionContext) {
//			Cursor c = new Cursor(getCursor(), executionContext);
//			System.out.println(c.getParent(2).toString());
//			return variableDeclarations;
//		}
	}
}
