package com.reactivebbq.customers

//Use default execution context - tune later

//#customer-routes-class
trait CustomerRoutes extends JsonSupport {

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[CustomerRoutes])

  // other dependencies that CustomerRoutes use
  def customerRegistryActor: ActorRef

  // Required by the `ask` (?) method below
  implicit lazy val timeout = Timeout(60.seconds) // usually we'd obtain the timeout from the system's configuration

  //#all-routes
  //#customers-get-post
  //#customers-get-delete
  lazy val customerRoutes: Route =
  pathPrefix("customers") {
    //POST /customers/
    post {
      entity(as[CreateCustomerDTO]) { customerDTO =>
        val newCustomer = customerDTO.toCustomer
        onComplete(customerRegistryActor ? NewCustomer(Some(newCustomer))) {
          case Success(response) => response match {
            case cc: CustomerCreated =>
              complete(StatusCodes.Created, Some(CustomerCreatedDTO.fromCustomerCreated(cc)))
            case cae: CustomerAlreadyExists =>
              complete(StatusCodes.BadRequest, Some(CustomerAlreadyExistsDTO.fromCAE(cae)))
          }
          case Failure(err) =>
            err.printStackTrace()
            complete(StatusCodes.InternalServerError, "Message: " + err.getMessage)
        }
      }
    } ~
      pathPrefix(Segment) { customerId =>
        pathEnd {
          //GET /customers/(customerID/username)
          get {
            //#retrieve-customer-info
            val futureMaybeCustomer: Future[Option[GetCustomerResponse]] =
              (customerRegistryActor ? GetCustomer(customerId))
                .mapTo[Option[GetCustomerResponse]]

            rejectEmptyResponse {
              complete(
                futureMaybeCustomer
                  .map(GetCustomerResponseDTO.fromGetCustomerResponse))
            }
            //#retrieve-customer-info
          }
        } ~
          path("addorder") {
            //PUT /customers/(customerID/username)/addorder
            put {
              entity(as[AddOrderDTO]) { addOrder =>
                val futureMaybeOrderAdded =
                  (customerRegistryActor ? AddOrder(customerId, addOrder.orderId)).mapTo[Option[OrderAdded]]

                rejectEmptyResponse {
                  complete(futureMaybeOrderAdded.map(OrderAddedDTO.fromOrderAdded))
                }

              }
            }
          } ~
          //GET /customers/(customerID/username)/orders
          path("orders") {
            get {
              val futureMaybeOrders =
                (customerRegistryActor ? GetAllOrders(customerId)).mapTo[Option[GetOrdersResponse]]

              rejectEmptyResponse {
                complete(futureMaybeOrders.map(OrdersDTO.fromOrdersResponse))
              }

            }
          }
      } //GET /customers/(customerID/username)

  }
}
