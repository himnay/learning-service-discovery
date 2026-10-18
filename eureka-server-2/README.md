# <span style="color:hsl(201,80%,58%)">eureka-server-2</span>

Peer 2 of the two-node Eureka registry. Same code as [`eureka-server-1`](../eureka-server-1/README.md) — ***read that
README***, it covers both nodes.

|                            | eureka-server-1            | eureka-server-2            |
|----------------------------|----------------------------|----------------------------|
| Port                       | 8761                       | ***8762***                 |
| `eureka.instance.hostname` | `localhost`                | `127.0.0.1`                |
| `defaultZone` (peer)       | `127.0.0.1:8762`           | `localhost:8761`           |
| Log file                   | `logs/eureka-server-1.log` | `logs/eureka-server-2.log` |
| Dashboard                  | http://localhost:8761      | http://localhost:8762      |

```bash
mvn -pl eureka-server-2 spring-boot:run
```

- ***Keep the two in sync*** — any change to code or shared config in one module must be made in the other;
  `diff -r eureka-server-1/src eureka-server-2/src` should show only the rows above (plus the banner).
