package org.eclipse.buildship.core.workspace.internal

import java.lang.invoke.MethodHandleImpl.BindCaller.T

import spock.lang.Specification

import org.eclipse.buildship.core.workspace.internal.ManagedModelMergingStrategy.Result

class ManagedModelMergingStrategyTest extends Specification {

    def "Verify algorithm"(current, model, managed, expectedElements, expectedManaged) {
        when:
        Result<T> result = ManagedModelMergingStrategy.calculate(current as Set, model as Set, managed as Set)

        then:
        result.nextElements == expectedElements as Set
        result.nextManaged == expectedManaged as Set

        where:
        current | model | managed | expectedElements | expectedManaged
        [ ]     | [ ]   | [ ]     | [ ]              | [ ]
        [1]     | [ ]   | [ ]     | [1]              | [ ]
        [ ]     | [1]   | [ ]     | [1]              | [1]
        [1]     | [1]   | [ ]     | [1]              | [ ]
        [ ]     | [ ]   | [1]     | [ ]              | [1]
        [1]     | [ ]   | [1]     | [ ]              | [ ]
        [ ]     | [1]   | [1]     | [1]              | [1]
        [1]     | [1]   | [1]     | [1]              | [1]
    }
}
