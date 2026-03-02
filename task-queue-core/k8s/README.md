# Kubernetes Deployment

## Prerequisites

- Kubernetes cluster (minikube, kind, or cloud provider)
- kubectl configured
- Docker image built: `task-queue:latest`

## Quick Start

### 1. Build Docker Image
```bash
cd task-queue-core
docker build -t task-queue:latest .
```

### 2. Deploy to Kubernetes
```bash
# Apply all manifests
kubectl apply -f k8s/

# Or apply in order:
kubectl apply -f k8s/postgres-secret.yaml
kubectl apply -f k8s/postgres-pvc.yaml
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/redis-deployment.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

### 3. Verify Deployment
```bash
# Check pods
kubectl get pods

# Check services
kubectl get services

# View logs
kubectl logs -f deployment/task-queue-deployment
```

### 4. Access the Application
```bash
# Get service URL (minikube)
minikube service task-queue-service --url

# Or use port forwarding
kubectl port-forward service/task-queue-service 8080:80
```

Then access: http://localhost:8080/api/jobs

## Scaling
```bash
# Scale to 3 replicas
kubectl scale deployment task-queue-deployment --replicas=3
```

## Cleanup
```bash
kubectl delete -f k8s/
```

## Production Considerations

- Use external secrets management (e.g., Sealed Secrets, Vault)
- Use managed databases (RDS, Cloud SQL) instead of in-cluster PostgreSQL
- Use Redis cluster or managed Redis (ElastiCache, Cloud Memorystore)
- Configure resource limits based on load testing
- Set up horizontal pod autoscaling
- Use Ingress for external access
- Configure monitoring (Prometheus + Grafana)