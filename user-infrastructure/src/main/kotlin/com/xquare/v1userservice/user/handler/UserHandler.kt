package com.xquare.v1userservice.user.handler

import com.xquare.v1userservice.configuration.validate.RequestBodyValidator
import com.xquare.v1userservice.user.User
import com.xquare.v1userservice.user.router.dto.getuser.GetUserByAccountIdResponse
import com.xquare.v1userservice.user.router.dto.saveuser.CreateUserRequest
import com.xquare.v1userservice.user.saveuser.api.CreateUserInPendingStateProcessor
import com.xquare.v1userservice.user.saveuser.api.GetUserInformationService
import java.net.URI
import java.time.Year
import java.util.UUID
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyToMono
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait

@Component
class UserHandler(
    private val createUserInPendingState: CreateUserInPendingStateProcessor,
    private val getUserInformationService: GetUserInformationService,
    private val passwordEncoder: PasswordEncoder,
    private val requestBodyValidator: RequestBodyValidator
) {
    suspend fun saveUserHandler(serverRequest: ServerRequest): ServerResponse {
        val requestBody: CreateUserRequest = serverRequest.getCreateUserRequestBody()
        requestBodyValidator.validate(requestBody)
        processUserCreateSagaStep(requestBody)
        return ServerResponse.created(URI("/users")).buildAndAwait()
    }

    private suspend fun ServerRequest.getCreateUserRequestBody() =
        this.bodyToMono<CreateUserRequest>().awaitSingle()

    private suspend fun processUserCreateSagaStep(createUserRequest: CreateUserRequest) {
        val domainRequest = createUserRequest.toDomainUser()
        createUserInPendingState.processStep(domainRequest)
    }

    private suspend fun CreateUserRequest.toDomainUser() =
        User(
            password = passwordEncoder.encode(this.password),
            accountId = this.accountId,
            birthDay = this.birthDay,
            entranceYear = Year.now().minusYears(this.grade.toLong() - 1).value,
            profileFileName = this.profileImageUrl,
            num = this.num,
            grade = this.grade,
            name = this.name,
            classNum = this.classNum
        )

    suspend fun getUserByIdHandler(serverRequest: ServerRequest): ServerResponse {
        val userId = serverRequest.pathVariable("userId")
        val user = getUserInformationService.getUserById(UUID.fromString(userId))
        val userResponseDto = user.toGetUserByAccountIdResponseDto()
        return ServerResponse.ok().bodyValueAndAwait(userResponseDto)
    }

    suspend fun getUserByAccountIdHandler(serverRequest: ServerRequest): ServerResponse {
        val accountId = serverRequest.pathVariable("accountId")
        val user = getUserInformationService.getUserByAccountId(accountId)
        val userResponseDto = user.toGetUserByAccountIdResponseDto()
        return ServerResponse.ok().bodyValueAndAwait(userResponseDto)
    }

    private fun User.toGetUserByAccountIdResponseDto() =
        GetUserByAccountIdResponse(
            accountId = this.accountId,
            password = this.password,
            name = this.name,
            profileImageUrl = this.profileFileName,
            classNum = this.classNum,
            grade = this.grade,
            num = this.num,
            birthDay = this.birthDay,
            id = this.id
        )
}