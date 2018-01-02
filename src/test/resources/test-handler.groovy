import groovy.xml.MarkupBuilder

def xml = msg.xml
def child0 = xml.child[0]
def child1 = xml.child[1]

def writer = new StringWriter()
def xmlResponse = new MarkupBuilder(writer)

xmlResponse.'PARENT' {
    int i = 0;
    for (child in xml.child) {
        'CHILD'(id: i++, child)
    }
}

session.sendToQueue("out", writer.toString())