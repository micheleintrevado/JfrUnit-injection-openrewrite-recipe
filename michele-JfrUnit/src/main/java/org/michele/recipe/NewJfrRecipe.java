package org.michele.recipe;

import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.Comparator;

public class NewJfrRecipe extends Recipe {

	private static final String classAnnotationImport = "org.moditect.jfrunit.JfrEventTest";
	private static final String jfrEventsVariableImport = "org.moditect.jfrunit.JfrEvents";
	private static final String jfrAnnotationImport = "org.moditect.jfrunit.EnableEvent";

	@Override
	public String getDisplayName() {
		return "NewJfrRecipe";
	}

	@Override
	public String getDescription() {
		return "custom OpenRewrite recipe used to inject JfrUnit in JUnit 5 test.";
	}

	@Nullable
	@Override
	protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
		return new UsesType<>("org.junit.jupiter.api.Test", false);
	}

	@Override
	protected TreeVisitor<?, ExecutionContext> getVisitor() {
		return new JavaIsoVisitor<ExecutionContext>() {
			@Override
			public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
				J.ClassDeclaration c = super.visitClassDeclaration(classDecl, ctx);
				if (isApplicableAnnotationClass(c, getCursor())) {
					c = addClassAnnotation(c);
				}
				
				return c;
			}

			private J.ClassDeclaration addClassAnnotation(J.ClassDeclaration c) {
				maybeAddImport(classAnnotationImport);
				
				JavaTemplate jfrUnitClassAnnotationTemplate = JavaTemplate
						.builder(this::getCursor, "@JfrEventTest").imports("org.moditect.jfrunit.JfrEventTest").build();
				return c.withTemplate(jfrUnitClassAnnotationTemplate,
						c.getCoordinates().addAnnotation(Comparator.comparing(J.Annotation::getSimpleName)));
			}
			
		};
		
		
	}

	public static boolean isApplicableAnnotationClass(J.ClassDeclaration classDecl, Cursor cursor) {
		//check if the class already has a @JfrEventTest annotation
		boolean hasJfrUnitClassAnnotation = classDecl.getLeadingAnnotations().stream()
				.anyMatch(jfrClassAnnotation -> jfrClassAnnotation.getSimpleName().equals("JfrEventTest"));
		if (hasJfrUnitClassAnnotation) {
			return false;
		}
		
		return true;
	}

	/*private static boolean isBeanMethod(J.MethodDeclaration methodDecl) {
		for (J.Modifier m : methodDecl.getModifiers()) {
			if (m.getType() == J.Modifier.Type.Abstract || m.getType() == J.Modifier.Type.Static) {
				return false;
			}
		}
		for (J.Annotation a : methodDecl.getLeadingAnnotations()) {
			JavaType.FullyQualified aType = TypeUtils.asFullyQualified(a.getType());
			if (aType != null && BEAN_ANNOTATION_MATCHER.matchesAnnotationOrMetaAnnotation(aType)) {
				return true;
			}
		}
		return false;
	}*/
}