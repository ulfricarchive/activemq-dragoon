# activemq-dragoon
Dragoon CDI support for Apache ActiveMQ

# Low-level producer
```
public class Producer {

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
# Low-level consumer
```
public class Consumer {

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
			addBootHook(this::hearHello);
		}

		@Inject
		@Topic("hello")
		private MessageConsumer consumer;

		@Inject
		private MessageFactory factory;

		public void hearHello() {
			Message message = Try.toGet(consumer::receive);

			printMessage(message);
		}

		private void printMessage(Message message) {
			if (message == null) {
				System.out.println("null message");
			} else {
				if (message instanceof TextMessage) {
					TextMessage text = (TextMessage) message;
					System.out.println("Text message: " + Try.toGet(text::getText));
				} else {
					System.out.println("Message: " + message);
				}
			}
		}
	}

}
```

# Events
```
public class EventsExample {

	public static void main(String[] args) {
		try (ManagedContainer container = Container.launch()) {
			container.install(Cfg4jContainer.class);
			container.install(VaultContainer.class);
			container.install(ActiveContainer.class);

			container.install(Consumer.class);
			container.install(Producer.class);
		}
	}

	public static class Producer extends Container {
		@Inject
		@Topic("bans")
		private EventProducer<BanEvent> producer;

		public Producer() {
			addBootHook(() -> {
				BanEvent event = new BanEvent();
				event.setBanned(UUID.randomUUID());
				event.setExpiry(Instant.now().plus(Duration.ofDays(10)));

				producer.send(event);
			});
		}
	}

	public static class Consumer extends Container {
		@Inject
		@Topic("bans")
		private EventConsumer<BanEvent> consumer;

		public Consumer() {
			addBootHook(() -> {
				BanEvent event = consumer.receive();
				System.out.println("Banned " + event.getBanned() + " until " + event.getExpiry());
			});
		}
	}

	public static class BanEvent extends Event {
		private UUID banned;
		private Instant expiry;

		public UUID getBanned() {
			return banned;
		}

		public void setBanned(UUID banned) {
			this.banned = banned;
		}

		public Instant getExpiry() {
			return expiry;
		}

		public void setExpiry(Instant expiry) {
			this.expiry = expiry;
		}
	}

}
```
