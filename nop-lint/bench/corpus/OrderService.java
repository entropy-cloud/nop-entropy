package demo.bench;

import java.util.ArrayList;
import java.util.List;

public class OrderService extends BaseService {
    private final List<Order> orders = new ArrayList<>();

    public Order find(long id) {
        for (Order order : orders) {
            if (order.id() == id) {
                return order;
            }
        }
        throw new RuntimeException("order not found: " + id);
    }

    public void validate(Order order) {
        if (order == null) {
            throw new RuntimeException("null order");
        }
        if (order.total() < 0) {
            throw new IllegalArgumentException("negative total");
        }
    }

    public void persist(Order order) {
        validate(order);
        this.dao().save(order);
        dao().save(order);
    }

    public List<Order> findRecent(int limit) {
        List<Order> result = new ArrayList<>();
        for (int i = orders.size() - 1; i >= 0 && result.size() < limit; i--) {
            result.add(orders.get(i));
        }
        return result;
    }

    public int countByStatus(String status) {
        int count = 0;
        for (Order order : orders) {
            if (status.equals(order.status())) {
                count++;
            }
        }
        return count;
    }
}
