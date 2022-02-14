package io.supertokens.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class) // Remove this line for JUnit 5!!
@AnalyzeClasses(packages = "io.supertokens.inmemorydb.queries")
public class QueryTemplateUsageTest {

    // @formatter:off
    @ArchTest
    public static final ArchRule discourage_use_of_unsafe_methods_rule = noClasses()
            .that().resideInAnyPackage("io.supertokens.inmemorydb.queries")
            .should().callMethod(Connection.class, "prepareStatement", String.class)
            .orShould().callMethod(PreparedStatement.class, "executeQuery")
            .orShould().callMethod(PreparedStatement.class, "executeUpdate")
            .because("We consistently want to use QueryExecutorTemplate in our DB queries to avoid memory leaks");
    // @formatter:on

    /**/

//    public static final Class[] CLASSES = {EmailPasswordQueries.class,
//            EmailVerificationQueries.class,
//            GeneralQueries.class,
//            JWTSigningQueries.class,
//            PasswordlessQueries.class,
//            SessionQueries.class,
//            ThirdPartyQueries.class};
//    private static final JavaClasses classes = new ClassFileImporter().importClasses(
//            CLASSES
//    );
//    @ArchTest
//    public static final ArchRule rule2 =
//            noMethods().that()
//                    .areDeclaredIn(ConnectionPool.class)
//                    .and().haveName("getConnection")
//                    .should().
//                    .should(onlyBeCalledByTransactionMethods())
//                    .because("We want to avoid memory leaks");
////                    .check(classes);
//
//
//
//    @Test
//    public void discourage_use_of_unsafe_methods_rule_Test() {
//        final ArchRule discourage_use_of_unsafe_methods_rule =
//                noClasses()
//                        .that().resideInAnyPackage("io.supertokens.inmemorydb.queries")
//                        .should().callMethod(Connection.class, "prepareStatement", String.class)
//                        .orShould().callMethod(PreparedStatement.class, "executeQuery")
//                        .orShould().callMethod(PreparedStatement.class, "executeUpdate")
//                        .because("We consistently want to use QueryExecutorTemplate in our DB queries to avoid
//                        memory " +
//                                        "leaks");
//    }
//
//    private static final Logger logger = LoggerFactory.getLogger(QueryTemplateUsageTest.class);
//
//    @Test
//    public void demo() {
//        JavaClasses classes = new ClassFileImporter().importClasses(
//                EmailPasswordQueries.class,
//                EmailVerificationQueries.class,
//                GeneralQueries.class,
//                JWTSigningQueries.class,
//                PasswordlessQueries.class,
//                SessionQueries.class,
//                ThirdPartyQueries.class
//        );
//
////        methods().that().areDeclaredInClassesThat()
////                .resideInAnyPackage("io.supertokens.inmemorydb.queries")
////                .and().haveNameEndingWith("_Transaction")
////                .should(beAbleToMakeCallToConectionPool()).check(classes);
//
//        logger.info("call.getOrigin().getFullName()");
//
//        final GivenMethodsConjunction cp_METHODS = methods().that()
//                .areDeclaredIn("io.supertokens.inmemorydb.ConnectionPool.class");
//
////        final GivenMethodsConjunction getConnection1 =
//        cp_METHODS/*.and().areStatic()*/.should().haveName("getConnectin");
////        logger.info("Method EXIST " + getConnection1.toString());
////        final var getConnection = getConnection1
////                .should(onlyBeCalledByTransactionMethods());
////
////        logger.info("TEXT " + getConnection.toString());
////
////        getConnection.check(classes);
//
//        logger.info("THIS IS CALLED");
//    }
//
//    private static ArchCondition<JavaMethod> onlyBeCalledByTransactionMethods() {
//        return new ArchCondition<>("only be called by @Deprecated methods or constructors") {
//            @Override
//            public void check(JavaMethod method, ConditionEvents events) {
//                final var callsOfSelf = method.getCallsOfSelf();
//                logger.info("Calls of self " + callsOfSelf.size());
//                for (JavaMethodCall call : callsOfSelf) {
//                    logger.info(call.getOrigin().getFullName());
//                    logger.debug(call.getOrigin().getFullName());
//                    if (!call.getOrigin().getFullName().endsWith("_Transaction")) {
//                        events.add(SimpleConditionEvent.violated(method, call.getDescription()));
//                    }
//                }
//                for (JavaMethodCall call : method.getCallsOfSelf()) {
//                    logger.info("DEPRECTATED" + call.getOrigin().getFullName());
////                    logger.debug(call.getOrigin().getFullName());
//                    if (!call.getOrigin().isConstructor()) {
//                        if (!call.getOrigin().isAnnotatedWith(Deprecated.class)) {
//                            events.add(SimpleConditionEvent.violated(method, call.getDescription()));
//                        }
//                    }
//                }
//            }
//        };
//    }
//
//    //    class DemoTest {
//    @Test
//    public void demo2() {
//        JavaClasses classesToCheck = new ClassFileImporter().importClasses(Okay.class, NotOkay.class, MyInjector
//        .class);
//
//        methods().that().areDeclaredIn(MyInjector.class)
//                .and().haveName("getInstance")
//                .should(onlyBeCalledByDeprecatedMethodsOrConstructors()).check(classesToCheck);
//    }
//
//    private ArchCondition<JavaMethod> onlyBeCalledByDeprecatedMethodsOrConstructors() {
//        return new ArchCondition<JavaMethod>("only be called by @Deprecated methods or constructors") {
//            @Override
//            public void check(JavaMethod method, ConditionEvents events) {
//                for (JavaMethodCall call : method.getCallsOfSelf()) {
//                    logger.info(call.getOrigin().getFullName());
////                    logger.debug(call.getOrigin().getFullName());
//                    if (!call.getOrigin().isConstructor()) {
//                        if (!call.getOrigin().isAnnotatedWith(Deprecated.class)) {
//                            events.add(SimpleConditionEvent.violated(method, call.getDescription()));
//                        }
//                    }
//                }
//            }
//        };
//    }
//
//    static class Okay {
//        private final Object fromConstructor;
//        private Object fromMethod;
//
//        Okay() {
//            fromConstructor = MyInjector.getInstance(Object.class);
//        }
//
//        @Deprecated
//        void alsoOkay() {
//            fromMethod = MyInjector.getInstance(Object.class);
//        }
//    }
//
//    static class NotOkay {
//        private Object fromMethodWithoutAnnotation;
//
//        static void fromWrongMethod() {
//            MyInjector.getInstance(Object.class);
//        }
//    }
//
//    static class MyInjector {
//        static <T> T getInstance(Class<T> type) {
//            return null;
//        }
//    }
////    }
//
//
////    public static class ContainOnlyMethodsCallingPermissionClass extends ArchCondition<JavaClass> {
////
////        private final DescribedPredicate<JavaClass> isPermissionClass;
////
////        public ContainOnlyMethodsCallingPermissionClass(DescribedPredicate<JavaClass> isPermissionClass) {
////            super("only contain methods calling a permission class");
////            this.isPermissionClass = isPermissionClass;
////        }
////
////        @Override
////        public void check(JavaClass javaClass, ConditionEvents events) {
////            Set<String> methodIdentifiers = getMethodIdentifiersOfClass(javaClass);
////            methodIdentifiers.removeAll(collectMethodsCallingPermissionClass(javaClass));
////
////            for (String methodId : methodIdentifiers) {
////                events.add(new SimpleConditionEvent(javaClass, false, methodId));
////            }
////        }
////
////        private Set<String> collectMethodsCallingPermissionClass(JavaClass javaClass) {
////            Set<String> methodIdentifiers = new HashSet<>();
////
////            for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
////                if (!isCallFromMethod(access)) {
////                    continue;
////                }
////
////                if (isMethodCallToPermissionClass(access)) {
////                    JavaMethod callingMethod = (JavaMethod) access.getOwner();
////                    methodIdentifiers.remove(callingMethod.getFullName());
////                }
////            }
////
////            return methodIdentifiers;
////        }
////
////        private Set<String> getMethodIdentifiersOfClass(JavaClass javaClass) {
////            return javaClass.getMethods().stream().filter(method -> !method.isConstructor())
////                    .map(method -> method.getFullName()).collect(Collectors.toSet());
////        }
////
////        private boolean isCallFromMethod(JavaAccess<?> access) {
////            return access.getOrigin() instanceof JavaMethod;
////        }
////
////        private boolean isMethodCallToPermissionClass(JavaAccess<?> access) {
////            return isPermissionClass.apply(access.getTargetOwner());
////        }
////    }

}
