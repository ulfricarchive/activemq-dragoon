# activemq-dragoon
Dragoon CDI support for Apache ActiveMQ

```
public class Publisher {

	public static void main(String[] args) {
		try (ManagedContainer container = Container.launch()) {
			container.install(Cfg4jContainer.class);
			container.install(VaultContainer.class);
			container.install(ActiveContainer.class);

			container.install(Example.class);
		}
	}

	public static class Example extends Container {
		public Example() {
			addBootHook(this::sayHello);
		}

		@Inject
		@Topic("hello")
		private MessageProducer producer;

		@Inject
		private MessageFactory factory;

		public void sayHello() {
			Try.toRun(() -> producer.send(factory.createTextMessage("Hello, world!")));
		}
	}

}
```
