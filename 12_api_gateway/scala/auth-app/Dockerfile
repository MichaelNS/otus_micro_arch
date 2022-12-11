FROM michaelns/my-minimalka:jdk17

# libsodium.so: cannot open shared object file: No such file or directory, shutting down JVM since 'akka.jvm-exit-on-fatal-error' is enabled for ActorSystem[application]
# https://github.com/wireapp/echo-bot/issues/42
# You need to specify the: -Djava.library.path=$(LD_LIBRARY_PATH) when running your java run command.
# $(LD_LIBRARY_PATH) is your dir where libsodium.so, libcryptobox.so and libcryptobox-jni.so are

# You can find those libs for Ubuntu xenia here:
# https://github.com/wireapp/cryptobox4j
RUN apt-get update
RUN apt-get -y install libsodium-dev

COPY svc /svc

EXPOSE 9000
