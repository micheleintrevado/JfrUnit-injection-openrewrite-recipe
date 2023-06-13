package org.michele.recipe;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class JfrRecipe extends Recipe {

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
		return "OldJfrRecipe";
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

		// annotation to add before the class
		private final JavaTemplate jfrUnitClassAnnotationTemplate = JavaTemplate
				.builder(this::getCursor, "@org.moditect.jfrunit.JfrEventTest")
				.imports("org.moditect.jfrunit.JfrEventTest").build();

		// JfrEvents variable used to collect all the the JFR events during the tests
		// execution
		private final JavaTemplate jfrEventsVariableTemplate = JavaTemplate
				.builder(this::getCursor,
						"public org.moditect.jfrunit.JfrEvents jfrEvents = new org.moditect.jfrunit.JfrEvents();")
				.imports("org.moditect.jfrunit.JfrEvents").build();

		// type of JFR events that will be recorded
		private final JavaTemplate jfrUnitMethodAnnotationTemplate = JavaTemplate
				.builder(this::getCursor, "@org.moditect.jfrunit.EnableEvent(\"jdk.*\")")
				.imports("org.moditect.jfrunit.EnableEvent").build();

		private final JavaTemplate addAwaitEventsTemplate = JavaTemplate
				.builder(this::getCursor, "jfrEvents.awaitEvents();").build();

		// VISIT PARTS OF THE LST like class declarations, method declarations, etc...

		@Override
		public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration c, ExecutionContext executionContext) {

			J.ClassDeclaration classDecl = super.visitClassDeclaration(c, executionContext);
			boolean isTestClass = false;

			// check if the class already has a @JfrEventTest annotation. Don't make any
			// changes if there is the @JfrEventTest annotation.
			boolean hasJfrUnitClassAnnotation = classDecl.getLeadingAnnotations().stream()
					.anyMatch(jfrClassAnnotation -> jfrClassAnnotation.getSimpleName().equals("JfrEventTest"));

			// check if the tesitng class has the explicit "public" modifier. JfrUnit needs
			// it to work
			boolean hasExplicitPublicModifier = classDecl.hasModifier(J.Modifier.Type.Public);

			// check if the class is a testing class ( check if there is at least one testing method in the class)
			List<MethodDeclaration> classMethods = classDecl.getBody().getStatements().stream()
					.filter(statement -> statement instanceof J.MethodDeclaration)
					.map(stat -> (J.MethodDeclaration) stat).collect(Collectors.toList());

			for (J.MethodDeclaration method : classMethods) {
				for (J.Annotation annotation : method.getLeadingAnnotations()) {
					if ((annotation.getSimpleName().equals("Test"))
							|| (annotation.getSimpleName().equals("RepeatedTest"))
							|| (annotation.getSimpleName().equals("ParameterizedTest"))) {
						isTestClass = true;
					}
				}
			}

			if (isTestClass) {
				if (!hasJfrUnitClassAnnotation) {
					// write explicit "public" modifier to the class
					if (!hasExplicitPublicModifier) {
						ArrayList<J.Modifier> modifierList = new ArrayList<>();
						modifierList.add(new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Public,
								emptyList()));
						classDecl = classDecl.withModifiers(modifierList);
					}

					// add @JfrEventTest before the class
					maybeAddImport("org.moditect.jfrunit.JfrEventTest");
					classDecl = classDecl.withTemplate(jfrUnitClassAnnotationTemplate, classDecl.getCoordinates()
							.addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

				}

				if (classDecl.getBody() != null) {
					boolean hasJfrEventsVariable = classDecl.getBody().getStatements().stream()
							.anyMatch(variableStatement -> variableStatement.toString().equals(
									"public org.moditect.jfrunit.JfrEvents jfrEvents = new org.moditect.jfrunit.JfrEvents()"));
					if (!hasJfrEventsVariable) {
						// add the JFR events variable
						maybeAddImport("org.moditect.jfrunit.JfrEvents");
						classDecl = classDecl.withTemplate(jfrEventsVariableTemplate,
								classDecl.getBody().getCoordinates().firstStatement());
					}
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

			// check if the method is a testing method (check if it has some test annotations like @Test)
			boolean hasTestAnnotation = methodDecl.getLeadingAnnotations().stream()
					.anyMatch(methodAnnotation -> methodAnnotation.getSimpleName().equals("Test")
							|| methodAnnotation.getSimpleName().equals("ParameterizedTest")
							|| methodAnnotation.getSimpleName().equals("RepeatedTest"));

			boolean hasExplicitPublicModifier = methodDecl.hasModifier(J.Modifier.Type.Public);

			if ((!hasJfrUnitMethodAnnotation) && hasTestAnnotation) {

				if ((!hasExplicitPublicModifier) && (methodDecl.getBody() != null)) {
					ArrayList<J.Modifier> modifierList = new ArrayList<>();
					modifierList.add(new J.Modifier(randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Public,
							emptyList()));
					// add explicit "public" modifier to the method
					methodDecl = methodDecl.withModifiers(modifierList);
				}

				//add @EnableEvent("jdk.*") before the method
				maybeAddImport("org.moditect.jfrunit.EnableEvent");
				methodDecl = methodDecl.withTemplate(jfrUnitMethodAnnotationTemplate,
						methodDecl.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));

				if (methodDecl.getBody() != null) {
					boolean hasJfrUnitAwaitEventsStatement = methodDecl.getBody().getStatements().stream()
							.anyMatch(methodStatement -> methodStatement.toString().equals("jfrEvents.awaitEvents()"));
					if ((!hasJfrUnitAwaitEventsStatement) && hasTestAnnotation) {
						// add jfrEvents as last statement of the testing method
						methodDecl = methodDecl.withTemplate(addAwaitEventsTemplate,
								methodDecl.getBody().getCoordinates().lastStatement());
					}
				}
			}

			return methodDecl;
		}

	}
}
