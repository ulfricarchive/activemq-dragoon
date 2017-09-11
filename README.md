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

			container.install(Publisher.class);
			container.install(Subscriber.class);
		}
	}

	public static class Publisher extends Container {
		@Inject
		@Topic("bans")
		private EventPublisher<BanEvent> publisher;

		public Publisher() {
			addBootHook(() -> {
				Try.toRun(() -> Thread.sleep(100L));
				BanEvent event = new BanEvent();
				event.setBanned(UUID.randomUUID());
				event.setExpiry(Instant.now().plus(Duration.ofDays(10)));

				publisher.send(event);
			});
		}
	}

	public static class Subscriber extends Container {
		@Inject
		@Topic("bans")
		private EventSubscriber<BanEvent> subscriber;

		public Consumer() {
			addBootHook(() -> {
				new Thread(() -> {
					BanEvent event = subscriber.receive();
					System.out.println("Banned " + event.getBanned() + " until " + event.getExpiry());
				}).start();
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
